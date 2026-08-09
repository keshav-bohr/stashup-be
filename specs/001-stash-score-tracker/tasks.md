---

description: "Task list for Stash Score Tracker implementation"
---

# Tasks: Stash Score Tracker

**Input**: Design documents from `/specs/001-stash-score-tracker/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/openapi.yaml](./contracts/openapi.yaml), [quickstart.md](./quickstart.md)

**Tests**: Test tasks are included and are **mandatory**, not optional. Constitution Principle II
makes TDD non-negotiable: tests are written first, observed failing, and only then made to pass.
Every endpoint requires a contract test; coverage gates are 80% overall and 90% for `security`,
`score`, `period`, and `friendship`.

**Organization**: Tasks are grouped by user story so each story is independently implementable,
testable, and shippable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US4)
- Exact file paths are included in every task

## Path Conventions

Single Maven module, packaged by feature, per the Structure Decision in plan.md:

- Main: `src/main/java/com/stashup/<feature>/`
- Resources: `src/main/resources/`
- Migrations: `src/main/resources/db/migration/`
- Tests: `src/test/java/com/stashup/{contract,integration,unit,performance}/`

## Stack Reminders

Verified in research.md — the first two cost hours if forgotten:

- **`spring-boot-starter-webmvc`**, not `spring-boot-starter-web`. Boot 4 renamed the starters.
- **Maven 3.9.x**, not 4.x — Maven 4 is still pre-GA.
- Java 25 LTS (not 26), MySQL 8.4 LTS, Spring Boot 4.1.x on Spring Framework 7.
- Test starters are per-starter: `spring-boot-starter-webmvc-test`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Buildable, gated project skeleton

- [X] T001 Create Maven project at repository root with `pom.xml` declaring Java 25, Spring Boot 4.1.x parent, and starters `spring-boot-starter-webmvc`, `-data-jpa`, `-security`, `-validation`, `-actuator`, plus `mysql-connector-j`, `flyway-core`, `flyway-mysql`
- [X] T002 Add Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`) pinned to Maven 3.9.x so the build is reproducible regardless of local install
- [X] T003 [P] Configure Spotless in `pom.xml` with a Java formatter, bound to the `verify` phase as build-failing
- [X] T004 [P] Configure Checkstyle in `config/checkstyle/checkstyle.xml` enforcing cyclomatic complexity ≤ 10 and file length ≤ 500 lines, wired into `pom.xml` as build-failing
- [ ] T005 [P] Configure Error Prone + NullAway over JSpecify annotations in `pom.xml`, with `-Xlint:all -Werror` on the compiler plugin
- [X] T006 [P] Configure JaCoCo in `pom.xml` with a global 80% line-coverage gate and 90% gates scoped to `com.stashup.security`, `com.stashup.score`, `com.stashup.period`, `com.stashup.friendship`
- [X] T007 [P] Configure OWASP dependency-check in `pom.xml` to fail the build on CVSS ≥ 7 (high/critical)
- [X] T008 [P] Create `.gitignore` (`target/`, `*.class`, `.idea/`, `*.iml`, `.DS_Store`, `.env*`) and `.dockerignore` (`target/`, `.git/`, `*.log`, `.env*`)
- [X] T009 Create `src/main/java/com/stashup/StashUpApplication.java` and `src/main/resources/application.yml` with `spring.threads.virtual.enabled=true`, Flyway enabled, and environment-only datasource and JWT secret binding

**Checkpoint**: `./mvnw verify` runs green on an empty project with all gates active

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Cross-cutting infrastructure and account identity. Every user story assumes an
authenticated caller, so authentication lives here rather than in a story.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Configuration & test harness

- [X] T010 Create `src/main/java/com/stashup/common/config/ApplicationProperties.java` as a validated `@ConfigurationProperties` record so the app fails fast at startup on missing or malformed required configuration
- [X] T011 [P] Create `src/test/java/com/stashup/support/MySqlTestBase.java` providing a shared Testcontainers MySQL 8.4 container and Flyway-migrated schema for all integration and repository tests (no H2)
- [ ] T012 [P] Create `src/test/java/com/stashup/support/TestDataFactory.java` with builders for users, entries, and periods used across test suites

### Common primitives

- [X] T013 [P] Create `src/main/java/com/stashup/common/money/Money.java` as a record of `long amountMinor` + `Currency`, with exact addition, subtraction, negation, and a same-currency guard that fails fast on mismatch
- [X] T014 [P] Create `src/main/java/com/stashup/common/id/UuidV7.java` generating time-ordered UUIDv7 values, plus `src/main/java/com/stashup/common/id/BinaryUuidConverter.java` mapping to `BINARY(16)`
- [X] T015 [P] Create `src/main/java/com/stashup/common/error/ErrorCode.java` enumerating the stable codes published in contracts/openapi.yaml (`AMOUNT_NOT_POSITIVE`, `DATE_IN_FUTURE`, `UNKNOWN_CATEGORY`, `CATEGORY_TYPE_MISMATCH`, `WITHDRAWAL_NOT_ALLOWED_FOR_TYPE`, `CURRENCY_MISMATCH`, `SELF_REQUEST`, `ALREADY_FRIENDS`, …)
- [X] T016 Create `src/main/java/com/stashup/common/error/GlobalExceptionHandler.java` emitting RFC 9457 `ProblemDetail` responses extended with `code` and `correlationId` for every failure path
- [X] T017 [P] Create `src/main/java/com/stashup/common/correlation/CorrelationIdFilter.java` assigning a correlation ID per request and binding it to MDC for structured JSON logs
- [ ] T018 [P] Create `src/main/java/com/stashup/common/correlation/LogRedaction.java` ensuring monetary amounts and PII are never written to logs, per Principle IV
- [X] T019 [P] Create `src/main/java/com/stashup/common/page/KeysetPage.java` and `CursorCodec.java` implementing opaque cursor encoding with a hard maximum page size of 100 that **rejects** over-limit requests rather than clamping

### Identity schema & authentication

- [X] T020 Create `src/main/resources/db/migration/V1__identity.sql` creating `app_user`, `refresh_token`, and `idempotency_record` exactly as specified in data-model.md, including all indexes and `ON DELETE CASCADE` from `app_user`
- [X] T021 [P] Create `src/main/java/com/stashup/user/AppUser.java` entity and `src/main/java/com/stashup/user/AppUserRepository.java`, with `base_currency` immutable after creation
- [X] T022 [P] Create `src/main/java/com/stashup/security/RefreshToken.java` and `RefreshTokenRepository.java` storing only a SHA-256 hash of the opaque token, never the token itself
- [X] T023 Create `src/main/java/com/stashup/security/SecurityConfig.java` with Spring Security 7 defaulting to `denyAll`, permitting only `/auth/register`, `/auth/login`, `/auth/refresh`, and the actuator health endpoints, each individually annotated with its justification
- [X] T024 Create `src/main/java/com/stashup/security/JwtService.java` issuing 15-minute access tokens and `src/main/java/com/stashup/security/CurrentUser.java` resolving the authenticated user ID for injection into controllers
- [X] T025 Create `src/main/java/com/stashup/security/AccountLockoutService.java` recording failed login attempts in `app_user` so lockout is exact across instances, not per-JVM
- [X] T026 Create `src/main/java/com/stashup/common/idempotency/IdempotencyInterceptor.java` and `IdempotencyRecordRepository.java`: same key + same fingerprint replays the stored response, same key + different fingerprint returns 409
- [X] T027 [P] Create `src/main/java/com/stashup/common/ratelimit/RateLimitFilter.java` using Bucket4j with in-memory buckets on all state-changing endpoints, behind an interface that a Redis `ProxyManager` can later replace without call-site changes (see Complexity Tracking in plan.md)

### Contract tests then implementation for auth

- [X] T028 [P] Write contract tests in `src/test/java/com/stashup/contract/AuthContractTest.java` for `POST /auth/register|login|refresh|logout`, asserting schemas, status codes, that unknown-email and wrong-password return an identical 401, and that a locked account returns 423 — **observe these failing before T029**
- [X] T029 Create `src/main/java/com/stashup/security/AuthController.java` and `AuthService.java` implementing registration, login, refresh rotation, and logout revocation to satisfy T028
- [X] T030 [P] Write contract tests in `src/test/java/com/stashup/contract/ProfileContractTest.java` for `GET|PATCH|DELETE /me`, asserting that `baseCurrency` is rejected on PATCH — then create `src/main/java/com/stashup/user/UserController.java` and `UserService.java` implementing them, with DELETE cascading per FR-003

### Observability & schema verification

- [X] T031 [P] Configure Micrometer metrics, Micrometer Tracing, and Actuator health/readiness endpoints in `src/main/resources/application.yml`, with structured JSON logging in `src/main/resources/logback-spring.xml`
- [ ] T032 [P] Add springdoc-openapi to `pom.xml` and create `src/test/java/com/stashup/contract/OpenApiContractDiffTest.java` failing the build when the generated schema diverges from `specs/001-stash-score-tracker/contracts/openapi.yaml`

**Checkpoint**: A user can register, log in, refresh, view and delete their profile. Every other
endpoint returns 401. Foundation ready — user stories can now begin.

---

## Phase 3: User Story 1 - Record and review money movements (Priority: P1) 🎯 MVP

**Goal**: A user records income, expenses, savings, investments, and deductions, then reads
monthly and yearly summaries with totals by type and by category.

**Independent Test**: Create an account, add entries of each type across two months, then open the
monthly and yearly summaries and confirm totals, category breakdowns, and filtered lists match.
Delivers a working expense tracker even if nothing else ships.

### Tests for User Story 1 ⚠️ Write first, observe failing

- [X] T033 [P] [US1] Contract tests in `src/test/java/com/stashup/contract/CategoryContractTest.java` for `GET|POST /categories` and `DELETE /categories/{id}`, including 409 when a category is still referenced
- [X] T034 [P] [US1] Contract tests in `src/test/java/com/stashup/contract/EntryContractTest.java` for all five `/entries` operations, asserting each rejection code from T015 and that a foreign entry ID returns **404, not 403**
- [X] T035 [P] [US1] Contract tests in `src/test/java/com/stashup/contract/SummaryContractTest.java` for `GET /summaries/{period}` covering both `YYYY-MM` and `YYYY` forms
- [X] T036 [P] [US1] Unit tests in `src/test/java/com/stashup/unit/EntryValidationTest.java` for amount > 0, future date rejection, direction/type compatibility, category-type match, and currency match
- [X] T037 [P] [US1] Integration test in `src/test/java/com/stashup/integration/EntryLifecycleIT.java` covering create → list → filter → edit → delete and asserting summaries change correctly at each step, including the spec's edit and delete scenarios
- [X] T038 [P] [US1] Integration test in `src/test/java/com/stashup/integration/IdempotencyIT.java` asserting a replayed `Idempotency-Key` creates exactly one entry and a reused key with a different body returns 409
- [X] T039 [P] [US1] Repository test in `src/test/java/com/stashup/integration/EntryAuthorizationIT.java` asserting no finder can return another user's entry — the data-layer authorization guarantee from Principle IV

### Implementation for User Story 1

- [X] T040 [US1] Create `src/main/resources/db/migration/V2__categories_and_entries.sql` creating `category` and `financial_entry` per data-model.md, including the three composite indexes and the `CHECK` constraints on amount, date, and direction/type
- [X] T041 [US1] Create `src/main/resources/db/migration/V3__period_summary.sql` creating `period_summary` with **all** columns from data-model.md — score, band, and proportion nullable so User Story 2 needs no schema change
- [X] T042 [P] [US1] Create `src/main/java/com/stashup/category/Category.java`, `CategoryRepository.java`, and a Flyway seed of system categories owned by the **nil UUID** sentinel (not `NULL`, which would defeat the unique index)
- [X] T043 [P] [US1] Create `src/main/java/com/stashup/entry/FinancialEntry.java` entity with `entryType`, `direction`, `amountMinor`, `currency`, and `entryDate` as a `LocalDate` — a calendar date, never a timestamp
- [X] T044 [US1] Create `src/main/java/com/stashup/entry/FinancialEntryRepository.java` where **every** finder takes the owning user ID as a parameter; no unscoped `findById` may exist
- [X] T045 [US1] Create `src/main/java/com/stashup/entry/EntryValidator.java` enforcing the rules tested in T036, each failure mapped to its `ErrorCode`
- [X] T046 [US1] Create `src/main/java/com/stashup/category/CategoryService.java` and `CategoryController.java` implementing T033, resolving visible categories as `user_id IN (:userId, NIL_UUID)`
- [X] T047 [US1] Create `src/main/java/com/stashup/period/PeriodSummaryRecomputeService.java` computing `money_in`, `expense`, `saving_net`, `investment_net`, `deduction`, `stashed`, `outflow`, `gap`, and `entry_count` for one user-month via a single indexed `GROUP BY`, setting `completeness` to `INSUFFICIENT_DATA` when `money_in` is zero and `COMPLETE` otherwise
- [X] T048 [US1] Create `src/main/java/com/stashup/entry/EntryService.java` invoking T047 **inside the same transaction** as every insert, update, and delete — and recomputing **both** months when an edit moves `entryDate` across a month boundary
- [X] T049 [US1] Create `src/main/java/com/stashup/entry/EntryController.java` implementing all five operations with keyset pagination via T019 and the idempotency interceptor from T026
- [X] T050 [US1] Create `src/main/java/com/stashup/period/PeriodSummaryService.java` and `SummaryController.java` serving `GET /summaries/{period}`, reading materialised type totals and computing the category breakdown on demand with an indexed `GROUP BY category_id`
- [X] T051 [US1] Add yearly summary support in `src/main/java/com/stashup/period/PeriodSummaryService.java` by summing the twelve monthly rows and reporting `contributingMonths`
- [X] T052 [US1] Add `src/main/java/com/stashup/period/PeriodParser.java` validating the `YYYY-MM` / `YYYY` path parameter and rejecting malformed values with a 400

**Checkpoint**: User Story 1 fully functional — a usable expense tracker. All T033–T039 green.

---

## Phase 4: User Story 2 - See my stash score (Priority: P2)

**Goal**: A 0–100 score for any month or year, equal to the proportion of money in that was
converted into savings and investments, with the inputs that produced it.

**Independent Test**: Seed a user with known entries, request the score, and verify it and its
component inputs match the documented rules. Then verify the yearly score aggregates correctly.

### Tests for User Story 2 ⚠️ Write first, observe failing

- [X] T053 [P] [US2] Unit test in `src/test/java/com/stashup/unit/ScoreProportionTest.java` asserting the spec's decisive case: income 100 / stashed 10 scores **10**, income 10 / stashed 5 scores **50**. A higher earner must not win on absolute amount (FR-014, FR-015)
- [X] T054 [P] [US2] Unit test in `src/test/java/com/stashup/unit/ScoreEdgeCaseTest.java` covering the 100 cap when stashed ≥ money in, the 0 floor when net stashed ≤ 0, `INSUFFICIENT_DATA` when no income, and `score = 0` with `COMPLETE` when income exists but nothing was stashed — asserting these three "zero-ish" states stay distinguishable
- [X] T055 [P] [US2] Unit test in `src/test/java/com/stashup/unit/YearlyScoreTest.java` asserting the yearly score sums underlying money-in and stashed rather than averaging monthly scores: months of (1,000 / 500) and (100,000 / 5,000) must yield ≈ 5, not ≈ 27
- [X] T056 [P] [US2] Unit test in `src/test/java/com/stashup/unit/ScoreBandTest.java` asserting the five equal bands and identical band boundaries for every user
- [X] T057 [P] [US2] Unit test in `src/test/java/com/stashup/unit/NetStashTest.java` asserting a 10,000 deposit followed by an 8,000 withdrawal in one month counts net 2,000, and that a negative net month contributes zero
- [X] T058 [P] [US2] Contract tests in `src/test/java/com/stashup/contract/ScoreContractTest.java` for `GET /scores/{period}` and `GET /scores`, asserting `inputs`, `capped`, `changeFromPreviousPeriod`, and `contributingMonths` are present and correctly typed
- [X] T059 [P] [US2] Integration test in `src/test/java/com/stashup/integration/BackdatedScoreIT.java` asserting an entry backdated into a closed month recalculates that month's score, visible in the write response itself rather than after a delay

### Implementation for User Story 2

- [X] T060 [P] [US2] Create `src/main/java/com/stashup/score/ScoreCalculator.java` computing `proportion_bp = min(10000, round(stashed × 10000 / money_in))` in integer arithmetic with no floating point, and `score = round(proportion_bp / 100)`
- [X] T061 [P] [US2] Create `src/main/java/com/stashup/score/ScoreBand.java` defining the five equal bands over 0–100
- [X] T062 [US2] Extend `src/main/java/com/stashup/period/PeriodSummaryRecomputeService.java` (T047) to populate `proportion_bp`, `score`, and `band`, leaving them null when `money_in` is zero
- [X] T063 [US2] Create `src/main/java/com/stashup/score/ScoreService.java` serving monthly scores from `period_summary` and deriving yearly scores by summing underlying figures across the year's rows
- [X] T064 [US2] Add `changeFromPreviousPeriod` in `src/main/java/com/stashup/score/ScoreService.java`, returning null when the preceding period had no score rather than treating absence as zero
- [X] T065 [US2] Create `src/main/java/com/stashup/score/ScoreController.java` implementing `GET /scores/{period}` and `GET /scores`, returning the score to its owner regardless of completeness (FR-029)
- [X] T066 [US2] Add the `capped` flag to the score response DTO in `src/main/java/com/stashup/score/ScoreResponse.java` so a user can tell a genuine 100 from a capped one

**Checkpoint**: Scores work for months and years. All T053–T059 green. US1 still green.

---

## Phase 5: User Story 3 - Keep the score honest (Priority: P2)

**Goal**: Each period is checked against itself; unbalanced periods are flagged non-accusatorily,
resolved by recording missing income or acknowledging a drawdown, and excluded from friend rankings
until resolved.

**Independent Test**: Seed 40,000 income against 90,000 outflow, confirm the score returns flagged
with a 50,000 gap and a two-option prompt, then acknowledge a drawdown and confirm the period
becomes complete with the score value unchanged.

### Tests for User Story 3 ⚠️ Write first, observe failing

- [X] T067 [P] [US3] Unit test in `src/test/java/com/stashup/unit/ReconciliationToleranceTest.java` asserting the flag threshold is `max(10% of money_in, absolute_floor)`, including the small-income case the floor exists to suppress and the exact-boundary case, which must resolve deterministically and not oscillate
- [X] T068 [P] [US3] Unit test in `src/test/java/com/stashup/unit/AcknowledgmentScopeTest.java` asserting an acknowledgment holds while `gap ≤ acknowledged_gap + tolerance` and re-flags once the gap grows beyond it (FR-028)
- [X] T069 [P] [US3] Contract tests in `src/test/java/com/stashup/contract/ReconciliationContractTest.java` for `GET /periods/{period}/reconciliation` and `PUT|DELETE .../drawdown-acknowledgment`, asserting the prompt carries **exactly two** resolutions
- [X] T070 [P] [US3] Test in `src/test/java/com/stashup/unit/ReconciliationCopyTest.java` asserting prompt copy contains none of a denylist of accusatory terms and does not assert or imply dishonesty (FR-025) — the requirement most likely to drift during implementation
- [X] T071 [P] [US3] Integration test in `src/test/java/com/stashup/integration/ReconciliationFlowIT.java` covering flag → acknowledge → score unchanged → add expenses → re-flag, and separately flag → record missing income → complete with a recalculated score
- [X] T072 [P] [US3] Integration test in `src/test/java/com/stashup/integration/CappedScoreFlagsIT.java` asserting a month where stashed exceeds income is both capped at 100 and flagged `UNRECONCILED`
- [ ] T073 [P] [US3] Integration test in `src/test/java/com/stashup/integration/LongTermDrawdownIT.java` covering a user with months of no income spending from reserves, asserting acknowledgment is available per period and never reduces the score

### Implementation for User Story 3

- [X] T074 [US3] Create `src/main/resources/db/migration/V4__drawdown_acknowledgment.sql` creating `drawdown_acknowledgment` with `UNIQUE (user_id, period_start)` and cascade from `app_user`
- [X] T075 [P] [US3] Add `reconciliation.tolerance-percent` (default 10) and `reconciliation.absolute-floor-minor` (default 10000) to `ApplicationProperties` (T010) and `application.yml`, both externally tunable without a code change
- [X] T076 [P] [US3] Create `src/main/java/com/stashup/period/DrawdownAcknowledgment.java` and `DrawdownAcknowledgmentRepository.java`
- [X] T077 [US3] Create `src/main/java/com/stashup/period/ReconciliationService.java` computing the tolerance, deciding reconciled state, and deleting a stale acknowledgment once the gap closes so it cannot silently absorb a future gap
- [X] T078 [US3] Extend `src/main/java/com/stashup/period/PeriodSummaryRecomputeService.java` (T047) to set `completeness = UNRECONCILED` when the gap exceeds tolerance and is unacknowledged, without altering `score` (FR-029)
- [X] T079 [US3] Create `src/main/java/com/stashup/period/ReconciliationController.java` implementing the three reconciliation operations, with prompt copy reviewed against T070
- [X] T080 [US3] Add `completeness` to the score payload in `src/main/java/com/stashup/score/ScoreResponse.java` and `src/main/java/com/stashup/score/ScoreController.java` so the owner sees the state alongside the number

**Checkpoint**: Reconciliation works end to end. All T067–T073 green. US1 and US2 still green.

---

## Phase 6: User Story 4 - Add friends and compare (Priority: P3)

**Goal**: Users connect by request and acceptance, then see themselves and their friends ranked by
score for a period — with score, band, change, and streak, and nothing else.

**Independent Test**: Connect two users with known entries and confirm each sees the other's score,
band, change, and streak — and confirm that before the friendship exists neither can see the
other's score at all, and that no amount is ever retrievable.

### Tests for User Story 4 ⚠️ Write first, observe failing

- [X] T081 [P] [US4] Contract tests in `src/test/java/com/stashup/contract/FriendRequestContractTest.java` for `/friend-requests` operations, asserting `SELF_REQUEST` and `ALREADY_FRIENDS` rejections and that a request to a blocker returns **201**, so blocks are not detectable by probing
- [X] T082 [P] [US4] Contract tests in `src/test/java/com/stashup/contract/ComparisonContractTest.java` for `GET /comparison/{period}`, asserting `ranked`, `unranked`, and `streakLookbackMonths = 24` are present
- [X] T083 [P] [US4] **Privacy test** in `src/test/java/com/stashup/integration/ScoreVisibilityIT.java` asserting a non-friend, a pending requester, a blocked user, and a removed friend each receive no score and no band through search, comparison, or any other route (FR-036)
- [X] T084 [P] [US4] **Privacy test** in `src/test/java/com/stashup/integration/AmountLeakageIT.java` sweeping every friend-visible response and asserting no amount, income, category, or reconciliation gap appears for any user other than the caller (FR-037) — the highest-consequence test in the suite
- [X] T085 [P] [US4] Integration test in `src/test/java/com/stashup/integration/FriendshipLifecycleIT.java` covering request → accept → compare → remove, and request → decline where the requester cannot distinguish declined from unanswered
- [X] T086 [P] [US4] Integration test in `src/test/java/com/stashup/integration/SimultaneousRequestIT.java` asserting two users requesting each other concurrently produce exactly one accepted friendship, not a duplicate or an error
- [X] T087 [P] [US4] Integration test in `src/test/java/com/stashup/integration/BlockingIT.java` asserting a block hides both users from each other in search and comparison and prevents new requests
- [X] T088 [P] [US4] Integration test in `src/test/java/com/stashup/integration/ComparisonRankingIT.java` asserting only `COMPLETE` periods are ranked, that unreconciled and no-data friends appear in `unranked` with a reason rather than as zero or last place, and that all-unranked renders meaningfully
- [X] T089 [P] [US4] Unit test in `src/test/java/com/stashup/unit/StreakTest.java` asserting a streak counts consecutive complete months, resets on an unreconciled or empty month, and is capped at 24 with the cap declared in the response
- [X] T090 [P] [US4] Unit test in `src/test/java/com/stashup/unit/RankingTieTest.java` asserting two users at 30.4% and 30.6% both display 30 but rank deterministically by basis points

### Implementation for User Story 4

- [X] T091 [US4] Create `src/main/resources/db/migration/V5__friendship.sql` creating `friendship` with canonically ordered `(user_a_id, user_b_id)`, `UNIQUE` on that pair, and the two status indexes from data-model.md
- [X] T092 [P] [US4] Create `src/main/java/com/stashup/friendship/Friendship.java` and `FriendshipRepository.java`, with a canonical-ordering helper so no caller can insert an unordered pair
- [X] T093 [US4] Create `src/main/java/com/stashup/friendship/FriendshipService.java` implementing the state machine from data-model.md, interpreting a unique-constraint violation on a mutual request as an acceptance rather than an error
- [X] T094 [US4] Create `src/main/java/com/stashup/friendship/FriendVisibilityService.java` as the **single** gate through which another user's score may be read, requiring an `ACCEPTED` row — every cross-user read path must call it
- [X] T095 [US4] Create `src/main/java/com/stashup/friendship/FriendshipController.java` implementing friend requests, accept, decline, list, and remove
- [X] T096 [US4] Create `src/main/java/com/stashup/friendship/BlockController.java` implementing `POST /blocks` and `DELETE /blocks/{userId}`, where unblocking restores no friendship
- [X] T097 [US4] Add `GET /users` search to `src/main/java/com/stashup/user/UserController.java` (T030) returning identity only, excluding blockers, and never including a score or band
- [X] T098 [P] [US4] Create `src/main/java/com/stashup/score/StreakCalculator.java` computing consecutive complete months from `period_summary` with a bounded 24-month lookback
- [X] T099 [US4] Create `src/main/java/com/stashup/friendship/ComparisonService.java` fetching all participants' summaries for the period in a **single** indexed query, ranking by `proportion_bp`, and splitting non-rankable participants into `unranked` with a reason
- [X] T100 [US4] Create `src/main/java/com/stashup/friendship/ComparisonController.java` returning `ComparisonEntry` as the closed field set defined in contracts/README.md — adding a field here is the most likely way this product leaks financial data
- [X] T101 [US4] Extend account deletion in `src/main/java/com/stashup/user/UserService.java` (T030) to remove all `friendship` rows referencing the deleted user so they vanish from every other user's friend list and comparison view (FR-003)
- [X] T102 [US4] Add `streakLookbackMonths: 24` to the comparison response DTO in `src/main/java/com/stashup/friendship/ComparisonResponse.java` so the cap is declared rather than silently truncating

**Checkpoint**: All four user stories independently functional. Privacy tests T083 and T084 green.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T103 Create `src/test/java/com/stashup/performance/LatencyRegressionTest.java` seeding 3 years of entries and 50 friends, asserting reads p95 < 200 ms / p99 < 500 ms and writes p95 < 500 ms, with a 20% regression against the recorded baseline failing the build
- [ ] T104 [P] Add a query-count assertion in `src/test/java/com/stashup/performance/ComparisonQueryCountTest.java` proving the comparison view issues a **single** query over `period_summary` rather than fanning out per friend, so the materialisation design cannot be silently bypassed
- [X] T105 [P] Create `src/main/java/com/stashup/common/maintenance/PurgeScheduler.java` deleting expired refresh tokens and idempotency records older than 24 hours
- [X] T106 [P] Create `src/test/java/com/stashup/integration/AccountDeletionCascadeIT.java` asserting deletion removes entries, categories, summaries, acknowledgments, tokens, idempotency records, and friendships, leaving no orphan rows
- [ ] T107 [P] Create `src/test/java/com/stashup/integration/LogRedactionIT.java` asserting no monetary amount or PII reaches the log output on any code path, including error paths
- [ ] T108 [P] Add `Dockerfile` and `compose.yaml` for local MySQL 8.4 plus the application, matching the versions in quickstart.md
- [X] T109 [P] Create `README.md` at repository root covering setup, required environment variables, the build gates, and the Boot 4 starter-rename warning
- [ ] T110 Run every scenario in [quickstart.md](./quickstart.md) against a running instance and record the results, particularly scenario 2 (proportion fairness) and scenario 6 (privacy)
- [X] T111 Verify the JaCoCo gates configured in `pom.xml` pass at 80% overall and 90% for `security`, `score`, `period`, and `friendship`; add unit tests under `src/test/java/com/stashup/unit/` where short rather than lowering a threshold
- [X] T112 Run `./mvnw verify` clean with all gates active — Spotless, Checkstyle, Error Prone, NullAway, JaCoCo, OWASP, contract diff, and the latency suite

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — **blocks all user stories**
- **User Stories (Phases 3–6)**: All depend on Foundational
  - US1 (P1) → US2 (P2) → US3 (P2) → US4 (P3) is the sequential path
  - US2, US3, and US4 all read `period_summary`, created in US1
- **Polish (Phase 7)**: Depends on all desired stories being complete

### User Story Dependencies

Unlike the template's default, these stories are **not** fully independent — they form a data
chain, and pretending otherwise would produce tasks that cannot run:

- **US1 (P1)**: Depends only on Foundational. Ships alone as an expense tracker. **True MVP.**
- **US2 (P2)**: Needs `period_summary` from US1 (T041) and its recompute service (T047). Cannot be
  built first — there is nothing to score.
- **US3 (P2)**: Needs the `gap` computed in US1 (T047) and the `completeness` field written in US2.
  Independently testable once US1 exists; the reconciliation flow does not require scores to work,
  only the totals.
- **US4 (P3)**: Needs scores from US2 to have anything to compare. Friendship management alone
  (request/accept/block) could ship earlier, but the comparison view — the point of the story —
  cannot.

### Within Each User Story

- Tests are written and **observed failing** before implementation (Principle II)
- Migrations before entities → entities before repositories → repositories before services →
  services before controllers
- Cross-story extensions (T062, T078, T080) modify files created in earlier stories and must run
  sequentially with them

### Parallel Opportunities

- Phase 1: T003–T008 all parallel after T001–T002
- Phase 2: T011–T019 largely parallel; T013, T014, T015, T017, T018, T019 touch different files
- Every story's test block is fully parallel — they are separate files with no shared state
- Phase 3: T042 and T043 parallel after migrations; T040 and T041 are sequential (Flyway ordering)
- Phase 4: T060 and T061 parallel; T062–T066 sequential
- Phase 6: T092 and T098 parallel; the ten test tasks T081–T090 all parallel
- Phase 7: T104–T109 parallel

---

## Parallel Example: User Story 1

```bash
# Write all User Story 1 tests together, then observe them fail:
Task: "Contract tests for /categories in src/test/java/com/stashup/contract/CategoryContractTest.java"
Task: "Contract tests for /entries in src/test/java/com/stashup/contract/EntryContractTest.java"
Task: "Contract tests for /summaries in src/test/java/com/stashup/contract/SummaryContractTest.java"
Task: "Unit tests for entry validation in src/test/java/com/stashup/unit/EntryValidationTest.java"
Task: "Integration test for entry lifecycle in src/test/java/com/stashup/integration/EntryLifecycleIT.java"
Task: "Integration test for idempotency in src/test/java/com/stashup/integration/IdempotencyIT.java"
Task: "Repository test for entry authorization in src/test/java/com/stashup/integration/EntryAuthorizationIT.java"

# Then the two independent entity tasks together:
Task: "Create Category entity in src/main/java/com/stashup/category/Category.java"
Task: "Create FinancialEntry entity in src/main/java/com/stashup/entry/FinancialEntry.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1: Setup — buildable skeleton with gates active
2. Phase 2: Foundational — auth, error envelope, observability (**blocks everything**)
3. Phase 3: User Story 1
4. **STOP and VALIDATE**: run quickstart scenario 1 independently
5. A working expense tracker is deployable here, with no score and no friends

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. + US1 → expense tracker → **MVP, deployable**
3. + US2 → the stash score exists → quickstart scenarios 2, 3, 5
4. + US3 → scores are trustworthy → quickstart scenario 4
5. + US4 → the social loop closes → quickstart scenario 6
6. Polish → performance, cascade, redaction, docs

Shipping US4 before US3 is possible but inadvisable: it would put unverified scores into a friend
leaderboard, which is precisely the failure mode US3 exists to prevent.

### Parallel Team Strategy

Phase 2 is large and on the critical path for everyone, so split it rather than the stories:

1. Whole team completes Phase 1 together
2. Phase 2 splits cleanly three ways — common primitives (T013–T019), identity and auth
   (T020–T030), observability and schema verification (T031–T032)
3. After Phase 2: one developer takes US1 through US2 (they share `period_summary` and its
   recompute service, so splitting them causes conflicts), a second builds friendship management
   from US4 (T091–T097, which need no scores), a third writes the privacy and performance suites
   (T083, T084, T103, T104) against the contract before the implementations land
4. Converge on US3 and the comparison view once scores exist

---

## Notes

- `[P]` = different files, no dependencies on incomplete tasks
- `[Story]` labels map tasks to spec.md user stories for traceability
- Tests must be observed failing before implementation — Principle II is non-negotiable
- Commit after each task or logical group
- Stop at any checkpoint to validate a story independently
- The two tasks worth over-investing in are **T084** (amount leakage) and **T053** (proportion
  fairness). The first is the product's largest privacy risk; the second is the requirement most
  likely to be quietly reimplemented as an absolute-amount score.
