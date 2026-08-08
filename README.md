# StashUp Backend

Personal finance tracking with a proportion-based **stash score** and friends-only comparison.

The score is the share of the money that came in during a period which the user converted into
savings and investments, on a 0–100 scale. Someone earning 100 and stashing 10 scores **10**;
someone earning 10 and stashing 5 scores **50**. That is the point: everyone is measured on the
same baseline regardless of income.

- Specification: [specs/001-stash-score-tracker/spec.md](specs/001-stash-score-tracker/spec.md)
- Plan and design: [plan.md](specs/001-stash-score-tracker/plan.md) ·
  [data-model.md](specs/001-stash-score-tracker/data-model.md) ·
  [research.md](specs/001-stash-score-tracker/research.md)
- API contract: [contracts/openapi.yaml](specs/001-stash-score-tracker/contracts/openapi.yaml)
- Governance: [.specify/memory/constitution.md](.specify/memory/constitution.md)

## Stack

| | | Why |
|---|---|---|
| Java | **25 LTS** | Not 26 — it is newer but non-LTS and loses support in September 2026 |
| Spring Boot | **4.1.0** | On Spring Framework 7 |
| Maven | **3.9.x** | Not 4.x — still pre-GA as of August 2026 |
| MySQL | **8.4 LTS** | Not the 9.x/calendar innovation track, which has a short support tail |

## Running locally

```bash
docker run --name stashup-mysql -e MYSQL_ROOT_PASSWORD=local \
  -e MYSQL_DATABASE=stashup -p 3306:3306 -d mysql:8.4

export STASHUP_DB_URL=jdbc:mysql://localhost:3306/stashup
export STASHUP_DB_USERNAME=root
export STASHUP_DB_PASSWORD=local
export STASHUP_JWT_SECRET=$(openssl rand -base64 48)

./mvnw verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

All configuration is environment-only and validated at startup — the application fails fast on a
missing or malformed value rather than degrading later. Flyway applies migrations and seeds the
system categories on boot.

Validation scenarios are in [quickstart.md](specs/001-stash-score-tracker/quickstart.md).

## Tests

```bash
./mvnw test      # unit tests only, no database needed
./mvnw verify    # adds integration tests, formatting, lint, and coverage gates
```

Integration tests run against **real MySQL 8.4, never H2** — H2's compatibility mode diverges on
index behaviour, date handling, and constraint semantics, so a green H2 suite would say nothing
about migrations that had never touched the real engine.

Two ways to supply that engine:

- **Testcontainers** when a Docker daemon is running. This is the CI path.
- **An external MySQL** when it is not:
  ```bash
  STASHUP_TEST_DB_URL=jdbc:mysql://127.0.0.1:3306/stashup_test \
  STASHUP_TEST_DB_USERNAME=root STASHUP_TEST_DB_PASSWORD= ./mvnw verify
  ```

If neither is available the suite fails rather than silently falling back to an in-memory
database.

## Build gates

Every one of these fails the build; none is advisory.

| Gate | Tool |
|---|---|
| Formatting | Spotless |
| Complexity ≤ 10, file length ≤ 500, no hardcoded credentials, no float/double on members | Checkstyle |
| Zero compiler warnings | `-Xlint:all -Werror` |
| Coverage 80% overall, 90% for `security` / `score` / `period` / `friendship` | JaCoCo |
| Toolchain pinned to Java 25 and Maven 3.9.x | Enforcer |
| No high/critical CVEs | OWASP dependency-check, `-Psecurity-scan` (needs `NVD_API_KEY`) |

## Spring Boot 4 upgrade traps

Boot 4 modularised auto-configuration and moved to Jackson 3. Three things bit during this build
and will bite anyone following a pre-November-2025 tutorial:

1. **Starters were renamed.** `spring-boot-starter-web` → **`spring-boot-starter-webmvc`**
   (`-web-services` → `-webservices`, `-aop` → `-aspectj`).
2. **Auto-configuration is no longer bundled.** `flyway-core` alone gives you the library but no
   auto-configuration — without `org.springframework.boot:spring-boot-flyway`, migrations silently
   never run and Hibernate then fails validation against an empty schema. The same applies to
   Jackson (`spring-boot-jackson`).
3. **Jackson 3.** The `ObjectMapper` bean is `tools.jackson.databind.ObjectMapper`, not
   `com.fasterxml.jackson.databind.ObjectMapper`, and its exceptions are unchecked. Both Jackson 2
   and 3 are on the classpath transitively, so importing the wrong one compiles fine and fails at
   wiring time.

Also worth knowing: `spring-security-oauth2-jose` issues and verifies JWTs, but bearer-token
authentication needs `spring-security-oauth2-resource-server` as well.

## Design notes

- **Money** is integer minor units plus an explicit currency, everywhere. No floating point — and
  Checkstyle rejects `Float`/`Double` on members so it cannot creep back.
- **`entry_date` is a calendar `DATE`**, not a timestamp. The date a user assigns to a transaction
  is a fact about their calendar, so period membership is a plain date comparison. This removes
  timezone month-boundary ambiguity entirely.
- **Period totals are materialised** into `period_summary` and recomputed synchronously inside the
  entry-mutation transaction. The friend comparison then reads one indexed row per participant
  instead of aggregating per friend.
- **Yearly scores sum the underlying figures**, never average the monthly scores. Averaging would
  weight a 1,000-income month equally against a 100,000 one.
- **Score proportion is stored in basis points** so two users who both display 30 still rank
  deterministically.
- **Every repository finder is scoped by owning user.** There is no unscoped `findById` on an
  owned entity, and adding one is a review failure.
- **Another user's record returns 404, not 403** — a 403 would confirm the record exists.

## Status

User Story 1 (record and review) and User Story 2 (stash score) are implemented and tested.
User Story 3 (reconciliation) and User Story 4 (friends and comparison) are specified and planned
but not yet built. See [tasks.md](specs/001-stash-score-tracker/tasks.md) for the task-level state.
