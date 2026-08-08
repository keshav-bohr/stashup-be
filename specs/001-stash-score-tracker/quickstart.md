# Quickstart & Validation Guide: Stash Score Tracker

**Date**: 2026-08-08 | **Plan**: [plan.md](./plan.md) | **Contract**: [contracts/openapi.yaml](./contracts/openapi.yaml)

How to run the service locally and prove the feature works end to end. Implementation details
belong in `tasks.md`; this document is the run-and-verify guide.

## Prerequisites

| Tool | Version | Verified locally |
|---|---|---|
| JDK | 25 (LTS) | 25.0.2 ✓ |
| Maven | 3.9.x — **not** 4.x, still pre-GA | 3.9.12 ✓ |
| MySQL | 8.4 LTS | 8.4.6 ✓ |
| Docker | any current | 28.4.0 ✓ |

Docker is required for the test suite — integration and repository tests run against a real
MySQL 8.4 container via Testcontainers, not H2.

```bash
java -version   # expect 25.x
mvn -version    # expect 3.9.x
docker info     # must be running before `mvn verify`
```

## First run

```bash
# 1. Database (container is simplest; a local 8.4 instance works identically)
docker run --name stashup-mysql -e MYSQL_ROOT_PASSWORD=local \
  -e MYSQL_DATABASE=stashup -p 3306:3306 -d mysql:8.4

# 2. Required configuration — the app fails fast at startup if any is missing
export STASHUP_DB_URL=jdbc:mysql://localhost:3306/stashup
export STASHUP_DB_USERNAME=root
export STASHUP_DB_PASSWORD=local
export STASHUP_JWT_SECRET=$(openssl rand -base64 48)

# 3. Build, verify, run
./mvnw verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Flyway applies migrations on startup and seeds the system categories. Confirm the service is up:

```bash
curl -s localhost:8080/actuator/health   # {"status":"UP"}
curl -s localhost:8080/v3/api-docs | head -c 200
```

## Gates that must pass

`./mvnw verify` runs all of these; each is build-failing, not advisory.

| Gate | Enforced by |
|---|---|
| Formatting | Spotless |
| Complexity ≤ 10, file length ≤ 500 | Checkstyle |
| Nullness, strict lint | Error Prone + NullAway over JSpecify, `-Xlint:all -Werror` |
| Coverage 80% overall, 90% for `security` / `score` / `period` / `friendship` | JaCoCo |
| Contract tests, one per endpoint | MockMvc |
| Integration tests on real MySQL 8.4 | Testcontainers |
| Generated OpenAPI matches `contracts/openapi.yaml` | springdoc diff |
| No high/critical CVEs | OWASP dependency-check |
| Latency budget, 20% regression blocks | Seeded latency suite |

## Validation scenarios

Each scenario proves a specific requirement end to end. Set `T` to the access token from
registration or login, and `H` to `-H "Authorization: Bearer $T" -H 'Content-Type: application/json'`.

### 1. Record an entry and see it in the summary (User Story 1)

Register, then create an income and an expense entry, then read the month's summary.

```bash
curl -sX POST localhost:8080/api/v1/entries $H \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"entryType":"INCOME","amount":{"amountMinor":10000000,"currency":"INR"},
       "entryDate":"2026-08-01","categoryId":"<salary-category-id>"}'

curl -s localhost:8080/api/v1/summaries/2026-08 $H
```

**Expect**: `totalsByType.INCOME.total.amountMinor` is `10000000`, `entryCount` reflects both
entries, and category totals are broken out.

**Also verify**: a zero amount and a future `entryDate` are each rejected with `400` and a `code`
of `AMOUNT_NOT_POSITIVE` / `DATE_IN_FUTURE`.

### 2. The score is a proportion, not an amount (User Story 2 — the core case)

This is the scenario the whole scoring design exists to satisfy. Create two users:

- **User A**: income 100, stashed 10
- **User B**: income 10, stashed 5

```bash
curl -s localhost:8080/api/v1/scores/2026-08 -H "Authorization: Bearer $T_A"
curl -s localhost:8080/api/v1/scores/2026-08 -H "Authorization: Bearer $T_B"
```

**Expect**: A scores **10**, B scores **50**. The lower earner scores higher. If A scores higher
than B, the implementation has reverted to an absolute-amount score and FR-014/FR-015 are broken.

**Also verify**:
- `inputs` carries `moneyIn`, `stashed`, and `proportionBasisPoints` (FR-016).
- Income but no savings → `score: 0`, `completeness: COMPLETE`.
- Entries but no income → `score: null`, `completeness: INSUFFICIENT_DATA` — *not* zero.
- Stashing more than earned → `score: 100`, `capped: true`.
- Depositing 10,000 then withdrawing 8,000 in one month counts net 2,000.

### 3. Yearly score is not an average of monthly scores

Seed one month with income 1,000 / stashed 500 (score 50) and another with income 100,000 /
stashed 5,000 (score 5).

```bash
curl -s localhost:8080/api/v1/scores/2026 $H
```

**Expect**: score ≈ **5** — `(500 + 5000) / (1000 + 100000)`. If it returns ≈ 27, the
implementation averaged the two monthly scores, which weights a 1,000 month equally against a
100,000 one. `contributingMonths` should be `2`.

### 4. Reconciliation catches an unbalanced month (User Story 3)

Record 40,000 of income and 90,000 of combined outflow in one month.

```bash
curl -s localhost:8080/api/v1/periods/2026-08/reconciliation $H
```

**Expect**: `state: UNRECONCILED`, `gap.amountMinor` of 50,000 in minor units, and a `prompt` with
exactly two resolutions — `RECORD_MISSING_INCOME` and `ACKNOWLEDGE_DRAWDOWN`. Read the `message`
text: if it implies the user has been dishonest, it fails FR-025.

```bash
curl -sX PUT localhost:8080/api/v1/periods/2026-08/drawdown-acknowledgment $H
curl -s localhost:8080/api/v1/scores/2026-08 $H
```

**Expect**: `state: COMPLETE`, and the **score value is unchanged** by the acknowledgment (FR-026,
FR-029).

**Also verify**:
- A gap within tolerance never raises a prompt.
- Adding expenses after acknowledging, widening the gap past the acknowledged amount, re-flags the
  period (FR-028).
- Recording the missing income instead closes the gap and recalculates the score.

### 5. Backdated edits recalculate the right months

Add an entry dated in a closed month, then re-read that month's score.

**Expect**: the old month's score changes within the response of the write itself — recomputation
is in-transaction, not deferred. Moving an entry's `entryDate` across a month boundary must
recalculate **both** months; verify the source month's total drops.

### 6. Friends see scores; non-friends see nothing (User Story 4)

With A and B **not** friends:

```bash
curl -s "localhost:8080/api/v1/users?query=B" -H "Authorization: Bearer $T_A"
curl -s localhost:8080/api/v1/comparison/2026-08 -H "Authorization: Bearer $T_A"
```

**Expect**: search returns B's `displayName` and nothing else — no score, no band. The comparison
view does not include B at all.

Now connect them and re-read:

**Expect**: both appear in `ranked`, ordered by score, each with `changeFromPreviousPeriod` and
`completeMonthStreak`. `streakLookbackMonths` is present and equals 24.

**Also verify** — these are the privacy assertions that matter most:
- No response anywhere contains B's amounts, income, categories, or reconciliation gap.
- A friend whose month is `UNRECONCILED` appears in `unranked` with that reason, not as score 0.
- After A blocks B, B cannot find A in search, cannot see A in comparison, and cannot re-request.
- Removing a friend immediately removes score visibility both ways.

### 7. Retried writes do not duplicate

Send the same entry creation twice with the **same** `Idempotency-Key`.

**Expect**: identical `201` response both times, one entry in the list, unchanged summary totals.
Then send the same key with a **different** body — expect `409`, not a silent replay of the first
response.

### 8. Authorisation holds at the data layer

As user A, request one of B's entry IDs directly.

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  localhost:8080/api/v1/entries/<b-entry-id> -H "Authorization: Bearer $T_A"
```

**Expect**: `404`, not `403` — a 403 would confirm the ID exists. Repeat for summaries, scores, and
reconciliation. Every unauthenticated call to any non-auth endpoint must return `401`.

### 9. Performance budget

Seed a user with 3 years of entries and 50 friends, then measure the comparison and yearly summary
paths.

**Expect**: reads p95 < 200 ms, p99 < 500 ms; writes p95 < 500 ms. The comparison view should issue
a single indexed query over `period_summary` — if it fans out per friend, the materialisation
design has been bypassed.

## Troubleshooting

**`ClassNotFoundException` or a missing auto-configuration after adding a starter.** Spring Boot 4
renamed the starters and modularised auto-configuration. `spring-boot-starter-web` no longer exists
— it is **`spring-boot-starter-webmvc`**. Only the auto-configuration belonging to a declared
starter is loaded, so a bean you expected "for free" may need its module added explicitly.

**Tests fail with a connection error.** Testcontainers needs a running Docker daemon. There is no
H2 fallback, deliberately.

**Application exits at startup.** Required configuration is validated at boot and the service fails
fast rather than degrading. The log names the missing property.

**Flyway checksum mismatch.** Migrations are forward-only; an applied migration was edited in
place. Add a new migration instead, and reset the local database.
