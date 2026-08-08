# Phase 1 Data Model: Stash Score Tracker

**Date**: 2026-08-08 | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

Nine tables on MySQL 8.4. Every table that holds user-owned data carries `user_id`, and every index
on such a table leads with `user_id` — this is what makes the constitution's "authorisation at the
data-access layer" requirement a property of the schema rather than a convention.

## Conventions

- **Identifiers**: `BINARY(16)` primary keys holding **UUIDv7**, exposed to clients as opaque
  strings. UUIDv7 is time-ordered, so InnoDB's clustered index stays near-sequential — the insert
  locality of an auto-increment without exposing a guessable, enumerable integer.
- **Money**: every amount is `BIGINT` **minor units** with a companion `CHAR(3)` ISO 4217 currency.
  No `DECIMAL`, no floating point, anywhere.
- **Dates**: `entry_date` and `period_start` are `DATE` — calendar dates with no time component.
- **Instants**: `created_at`, `updated_at`, `acknowledged_at`, `expires_at` are
  `DATETIME(6)` in UTC.
- **Enums**: stored as `VARCHAR` with a `CHECK` constraint, not MySQL `ENUM` — reordering a MySQL
  `ENUM` is a schema migration hazard and the storage saving is irrelevant here.
- **Deletes**: hard deletes with `ON DELETE CASCADE` from `app_user`. FR-003 requires account
  deletion to actually remove the data, so soft deletion would be the wrong default.

---

## app_user

The person using the application. Owns everything else.

| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` PK | UUIDv7 |
| `email` | `VARCHAR(320)` | Unique, stored lowercased |
| `password_hash` | `VARCHAR(100)` | bcrypt or Argon2id; never logged, never returned |
| `display_name` | `VARCHAR(50)` | Shown to friends and in search results |
| `base_currency` | `CHAR(3)` | ISO 4217; set at registration, immutable thereafter |
| `timezone` | `VARCHAR(64)` | IANA zone; used by clients to default "today", not by scoring |
| `failed_login_count` | `INT` | Shared lockout state — exact across instances |
| `locked_until` | `DATETIME(6)` NULL | Set on repeated failures |
| `created_at` / `updated_at` | `DATETIME(6)` | UTC |

**Indexes**: `UNIQUE (email)`, `INDEX (display_name)` for prefix search.

**Rules**
- `base_currency` is immutable after registration. Changing it would silently reinterpret every
  historical amount, and there is no correct migration for that.
- Deleting a user cascades to entries, categories, summaries, acknowledgments, refresh tokens, and
  idempotency records, and removes every `friendship` row referencing them (FR-003).

---

## category

Labels grouping entries within a type. Either system-provided or user-created (FR-008).

| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` PK | UUIDv7 |
| `user_id` | `BINARY(16)` | Owning user, or the **nil UUID** for system categories |
| `entry_type` | `VARCHAR(16)` | Which entry type this category applies to |
| `name` | `VARCHAR(50)` | |
| `created_at` | `DATETIME(6)` | UTC |

**Indexes**: `UNIQUE (user_id, entry_type, name)`, `INDEX (user_id, entry_type)`.

**Rules**
- System categories use the **nil UUID** rather than `NULL` for `user_id`. MySQL treats `NULL`s as
  distinct in a unique index, so a nullable owner column would permit duplicate system category
  names. The sentinel makes the constraint actually hold.
- A category may not be deleted while entries reference it; the API returns a conflict naming the
  entry count.
- A user's visible categories are `user_id IN (:userId, NIL_UUID)`.

---

## financial_entry

One recorded money movement (FR-004 → FR-010).

| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` PK | UUIDv7 |
| `user_id` | `BINARY(16)` | Owner |
| `entry_type` | `VARCHAR(16)` | `INCOME` \| `EXPENSE` \| `SAVING` \| `INVESTMENT` \| `DEDUCTION` |
| `direction` | `VARCHAR(12)` | `CONTRIBUTION` \| `WITHDRAWAL` |
| `amount_minor` | `BIGINT` | Always **positive**; sign is carried by `direction`, never by the amount |
| `currency` | `CHAR(3)` | Must equal the owner's `base_currency` in v1 |
| `entry_date` | `DATE` | Calendar date, no time, not in the future |
| `category_id` | `BINARY(16)` | |
| `note` | `VARCHAR(500)` NULL | Optional |
| `created_at` / `updated_at` | `DATETIME(6)` | UTC |

**Indexes**
- `INDEX (user_id, entry_date DESC, id DESC)` — period aggregation and keyset pagination
- `INDEX (user_id, entry_type, entry_date)` — type-filtered listing and the summary rollup
- `INDEX (user_id, category_id, entry_date)` — category-filtered listing and breakdown

**Rules**
- `amount_minor > 0` (FR-005/FR-006). A zero or negative amount is a validation failure, not a
  withdrawal — withdrawals are expressed by `direction`.
- `entry_date <= CURRENT_DATE` in the owner's timezone.
- `direction = WITHDRAWAL` is only valid for `SAVING` and `INVESTMENT` (FR-009). `INCOME` is always
  `CONTRIBUTION`; `EXPENSE` and `DEDUCTION` are always `WITHDRAWAL` in the ledger sense but are
  stored as `CONTRIBUTION` to their own category — enforced by a `CHECK` so the combination cannot
  drift.
- `category_id` must resolve to a category whose `entry_type` matches this entry's.
- Any insert, update, or delete triggers recomputation of the affected `period_summary` row **in
  the same transaction**. An edit that moves `entry_date` across a month boundary recomputes
  **both** the old and the new month.

---

## period_summary

Materialised monthly totals and the derived score. **Granularity is always one month** — yearly
figures are computed on read by summing these rows, never stored, and never derived by averaging
monthly scores.

| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` PK | UUIDv7 |
| `user_id` | `BINARY(16)` | Owner |
| `period_start` | `DATE` | First day of the month |
| `currency` | `CHAR(3)` | Snapshot of the owner's base currency |
| `money_in_minor` | `BIGINT` | Σ `INCOME` |
| `expense_minor` | `BIGINT` | Σ `EXPENSE` |
| `saving_net_minor` | `BIGINT` | Σ saving contributions − Σ saving withdrawals; **may be negative** |
| `investment_net_minor` | `BIGINT` | Σ investment contributions − Σ divestments; may be negative |
| `deduction_minor` | `BIGINT` | Σ `DEDUCTION` |
| `stashed_minor` | `BIGINT` | `max(0, saving_net + investment_net)` (FR-017 floor) |
| `outflow_minor` | `BIGINT` | `expense + saving_net + investment_net + deduction` |
| `gap_minor` | `BIGINT` | `max(0, outflow − money_in)` (FR-023) |
| `proportion_bp` | `INT` NULL | Basis points, 0–10000; `NULL` when there is no income |
| `score` | `TINYINT UNSIGNED` NULL | 0–100, derived from `proportion_bp` |
| `band` | `VARCHAR(16)` NULL | Derived from `score` |
| `completeness` | `VARCHAR(20)` | `COMPLETE` \| `UNRECONCILED` \| `INSUFFICIENT_DATA` |
| `entry_count` | `INT` | Entries contributing to this row |
| `computed_at` | `DATETIME(6)` | UTC |

**Indexes**: `UNIQUE (user_id, period_start)` — serves the owner's single-period read, the
comparison view's `user_id IN (...) AND period_start = ?`, and the streak lookback's
`user_id IN (...) AND period_start BETWEEN ? AND ?`.

**Derivation** (all of it, in order):

```
stashed        = max(0, saving_net + investment_net)
outflow        = expense + saving_net + investment_net + deduction
gap            = max(0, outflow − money_in)
tolerance      = max(money_in × 10%, absolute_floor)     # both configurable
reconciled     = gap <= tolerance OR an acknowledgment covers gap

completeness   = INSUFFICIENT_DATA   if money_in == 0            # FR-018
               = UNRECONCILED        if not reconciled           # FR-024
               = COMPLETE            otherwise

proportion_bp  = null                if money_in == 0
               = min(10000, round(stashed × 10000 / money_in))   # FR-017 cap
score          = round(proportion_bp / 100)
band           = 0–19 | 20–39 | 40–59 | 60–79 | 80–100           # equal bands, all users
```

**Rules**
- `completeness` never alters `score` (FR-029). It gates comparison eligibility only.
- A month with no entries has **no row**. Absence means "no data", distinct from a row with
  `INSUFFICIENT_DATA` (entries exist but no income) and from `score = 0` (income exists, nothing
  stashed). All three render differently.
- The yearly rollup sums `money_in_minor` and `stashed_minor` across the year's rows and computes
  the proportion once. It reports the contributing month count (FR-020) and is `COMPLETE` only if
  every contributing month is.

---

## drawdown_acknowledgment

A user's confirmation that a period's gap is explained by money held before the period (FR-026).

| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` PK | UUIDv7 |
| `user_id` | `BINARY(16)` | Owner |
| `period_start` | `DATE` | Month being acknowledged |
| `acknowledged_gap_minor` | `BIGINT` | Gap size **at the moment of acknowledgment** |
| `acknowledged_at` | `DATETIME(6)` | UTC |

**Indexes**: `UNIQUE (user_id, period_start)`.

**Rules**
- The acknowledgment holds while `current_gap <= acknowledged_gap + tolerance`. Once the gap grows
  beyond that, the period flags again (FR-028) — the user acknowledged a 50,000 drawdown, not an
  unlimited one.
- Acknowledging changes no amount and no score (FR-026).
- If the gap later closes to zero because the missing income was recorded after all, the
  acknowledgment is deleted rather than left dormant, so it cannot silently absorb a future gap.

---

## friendship

One row per pair, canonically ordered (FR-030 → FR-039).

| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` PK | UUIDv7 |
| `user_a_id` | `BINARY(16)` | **Always the numerically lower** of the two IDs |
| `user_b_id` | `BINARY(16)` | Always the higher |
| `status` | `VARCHAR(12)` | `PENDING` \| `ACCEPTED` \| `BLOCKED` |
| `initiated_by_user_id` | `BINARY(16)` | Who sent the request |
| `blocked_by_user_id` | `BINARY(16)` NULL | Who blocked; set only when `status = BLOCKED` |
| `created_at` / `updated_at` | `DATETIME(6)` | UTC |

**Indexes**: `UNIQUE (user_a_id, user_b_id)`, `INDEX (user_b_id, status)`,
`INDEX (user_a_id, status)`.

**State transitions**

```
  (no row) ──send request──> PENDING ──accept──> ACCEPTED
                                │                    │
                          decline│                   │remove
                                ▼                    ▼
                            (row deleted)      (row deleted)

  any state ──block──> BLOCKED ──unblock──> (row deleted)
```

**Rules**
- Canonical ordering plus the unique constraint resolves the simultaneous-mutual-request race at
  the database: the second insert fails, and the application interprets that failure as the second
  user accepting the first user's request.
- A decline deletes the row and returns success to the requester regardless (FR-031) — the
  requester must not be able to distinguish declined from unanswered.
- `BLOCKED` suppresses both directions in search and comparison, and blocks new requests (FR-032).
- Score visibility requires a row with `status = ACCEPTED` (FR-036). Absence of a row, `PENDING`,
  and `BLOCKED` all yield the same outcome: no score, no band.

---

## refresh_token

Revocable session state backing short-lived JWT access tokens.

| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` PK | UUIDv7 |
| `user_id` | `BINARY(16)` | |
| `token_hash` | `CHAR(64)` | SHA-256 of the opaque token; the token itself is never stored |
| `expires_at` | `DATETIME(6)` | UTC |
| `revoked_at` | `DATETIME(6)` NULL | Set on logout, block, password change, account deletion |
| `created_at` | `DATETIME(6)` | UTC |

**Indexes**: `UNIQUE (token_hash)`, `INDEX (user_id, revoked_at)`, `INDEX (expires_at)` for purge.

---

## idempotency_record

Makes retried writes safe (FR-010).

| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` PK | UUIDv7 |
| `user_id` | `BINARY(16)` | |
| `idempotency_key` | `VARCHAR(64)` | Client-supplied |
| `request_fingerprint` | `CHAR(64)` | SHA-256 of the request body |
| `response_status` | `SMALLINT` | Replayed on a repeat |
| `response_body` | `JSON` | Replayed on a repeat |
| `created_at` | `DATETIME(6)` | UTC; retained 24 hours |

**Indexes**: `UNIQUE (user_id, idempotency_key)`, `INDEX (created_at)` for purge.

**Rules**
- Same key + same fingerprint → replay the stored response.
- Same key + **different** fingerprint → `409`, because the client reused a key for a different
  request and silently returning the old response would hide a client bug.
- Deduplication is by key only, never by content: two identical coffee purchases on the same day
  are both legitimate.

---

## Entity relationships

```
app_user 1──n financial_entry           n──1 category
   │  1──n period_summary                        │
   │  1──n drawdown_acknowledgment               │
   │  1──n refresh_token                  (nil UUID owner = system category)
   │  1──n idempotency_record
   │  1──n category (user-created)
   └──n friendship──n app_user           (canonically ordered pair)
```

## Requirement coverage

| Requirement | Where it lives |
|---|---|
| FR-004 → FR-006 | `financial_entry` type/amount/date constraints |
| FR-007 | Every finder scoped by `user_id`; no unscoped `findById` exists |
| FR-008 | `category` with nil-UUID system owner |
| FR-009 | `direction` column; `saving_net` / `investment_net` netting |
| FR-010 | `idempotency_record` unique constraint |
| FR-011, FR-012 | `period_summary` rollup + in-transaction recomputation |
| FR-013 → FR-022 | `period_summary` derivation block |
| FR-023 → FR-029 | `gap_minor`, `completeness`, `drawdown_acknowledgment` |
| FR-030 → FR-039 | `friendship` states and canonical ordering |
| FR-040 | `user_id` on every owned table; friendship check before cross-user reads |
| FR-041 | `amount_minor` + `currency` on every monetary column |
| FR-042 | Keyset pagination on `(user_id, entry_date DESC, id DESC)` |
| FR-043 | `computed_at` on summaries, `updated_at` on friendships |
