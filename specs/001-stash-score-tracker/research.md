# Phase 0 Research: Stash Score Tracker

**Date**: 2026-08-08 | **Plan**: [plan.md](./plan.md)

The user's direction was "Java latest and all the latest dependencies like Spring Boot, Maven, etc,
along with MySQL DB support." Every version below was verified against current sources rather than
assumed, because "latest" resolved differently from the obvious answer in three cases: the latest
Java is not the one to use, the latest Maven does not exist yet as a stable release, and the latest
MySQL is not the one for a financial system of record.

## Version selection

### Java 25 LTS — not Java 26

- **Decision**: Java 25, language level 25, toolchain pinned by Maven Enforcer.
- **Rationale**: JDK 26 reached GA on 2026-03-17 and is genuinely the latest release, but it is a
  six-month non-LTS that reaches end of support in **September 2026 — one month after this plan**.
  Starting a new production service on a runtime that stops receiving updates before the service
  ships would be indefensible. JDK 25 is the current LTS (GA 2025-09-16) with updates through
  September 2028. It is also what is installed locally (25.0.2), so there is no toolchain gap.
- **Alternatives considered**: Java 26 — rejected on the support window above. Java 21 LTS —
  rejected as needlessly behind; nothing in Spring Boot 4.1 requires staying there, and 25 brings
  mature virtual threads, which this workload benefits from directly.

### Spring Boot 4.1.x on Spring Framework 7

- **Decision**: Spring Boot 4.1.x (latest GA, published 2026-06-10).
- **Rationale**: Current GA line, built on Spring Framework 7, supports Java 17 through 26 so
  Java 25 is comfortably inside the window. Three Framework 7 features map directly onto
  constitution requirements rather than being adopted for novelty:
  - **Native API versioning** satisfies the constitution's rule that breaking changes ship behind
    a new API version, without a custom resolver.
  - **JSpecify null-safety annotations** give NullAway a standard vocabulary to enforce, which is
    the closest Java analogue to the constitution's "strict mode type checking" requirement.
  - **`@Retryable` and `@ConcurrencyLimit`** moved into the framework core, removing what would
    otherwise be a Spring Retry or Resilience4j dependency.
- **Critical migration detail**: Boot 4 **renamed the starters**. `spring-boot-starter-web` is now
  **`spring-boot-starter-webmvc`**, `-web-services` → `-webservices`, `-aop` → `-aspectj`. Every
  tutorial, generator template, and code sample predating November 2025 has the old names. This is
  the single most likely source of a wasted first hour.
- **Second migration detail**: auto-configuration is modularised — the monolithic
  `spring-boot-autoconfigure` jar is split into focused modules, so only the configuration
  belonging to a declared starter is loaded. This is a startup-time and memory win, but it means a
  bean you expected to appear "for free" may simply not be configured. Classic starter POMs exist
  as a transition aid; a greenfield project should use the modular starters directly and add what
  it needs deliberately.
- **Test starters**: Boot 4 ships a `-test` starter per regular starter. `spring-boot-starter-webmvc`
  pairs with `spring-boot-starter-webmvc-test`.
- **Alternatives considered**: Spring Boot 3.5.x — rejected; still supported but a new project has
  no reason to start a major version behind. Quarkus or Micronaut — outside the user's stated
  direction.

### Maven 3.9.x — Maven 4 is still a release candidate

- **Decision**: Maven 3.9.x (3.9.12 confirmed locally), Maven Wrapper committed so the build is
  reproducible independent of what any developer has installed.
- **Rationale**: This is the one place where "all the latest" cannot be honoured. **Maven 4.0.0 has
  not reached GA.** As of August 2026 the newest artifact is 4.0.0-rc-5 (documentation published
  2026-07-12), and the project's own position on the GA date is "when it's there." Building a
  financial service on a release candidate build tool trades a real risk for cosmetic currency.
- **Migration posture**: Maven 4 is a large architectural overhaul and is worth adopting once GA.
  The POM is written to be forward-compatible — no reliance on behaviours Maven 4 changes — so the
  upgrade should be a wrapper version bump.
- **Alternatives considered**: Maven 4.0.0-rc-5 — rejected as pre-GA. Gradle — outside the user's
  stated direction.

### MySQL 8.4 LTS — not the innovation track

- **Decision**: MySQL 8.4 LTS (8.4.11, released 2026-06-30). Same major/minor in local development,
  CI (Testcontainers), and production.
- **Rationale**: 8.4 is the current LTS line with five years of premier support, and is the
  recommended target now that MySQL 8.0 reached EOL in April 2026. The innovation track — which has
  moved to `YY.M` calendar versioning, so the July 2026 release is "MySQL 26.7" — ships features
  faster but carries a short support tail per release. A system of record for personal financial
  data is exactly the workload the LTS track exists for.
- **Alternatives considered**: MySQL 26.7 innovation — rejected on support tail. PostgreSQL —
  arguably better for this workload but explicitly outside the user's stated direction.

## Design research

### Score computation: materialise or compute on read?

- **Decision**: Materialise monthly totals and score into a `period_summary` row, recomputed
  **synchronously in the same transaction** as the entry mutation. Derive yearly figures by summing
  the monthly rows on read.
- **Rationale**: The comparison view is the deciding case. Fifty friends plus the user, for one
  period, computed on read, means 51 aggregations over the entries table per request — workable
  with good indexes, but it puts the product's most-visited screen permanently at the mercy of
  entry-table growth. Materialising turns it into one indexed range scan over 51 summary rows.
  Writes are the cheap side: a `GROUP BY` over one user's one month is bounded by that user's
  monthly entry count, not by table size.
- **Why synchronous rather than queued**: covered in the plan's Complexity Tracking. Briefly — the
  work is milliseconds, and in-transaction recomputation makes the summary consistent with the
  entries by construction rather than eventually.
- **Yearly scores must not average monthly scores.** The year's score is total stashed ÷ total money
  in for the year. Averaging twelve monthly percentages weights a month with 500 of income equally
  against a month with 50,000, which is simply a different and wrong number. The yearly rollup sums
  the underlying `money_in` and `stashed` figures and computes the proportion once.
- **Alternatives considered**: Full compute-on-read — rejected on the comparison path. Async queue
  recomputation — rejected as infrastructure ahead of need and as introducing staleness the
  synchronous approach avoids. Materialising per-category totals as well — rejected because the
  category breakdown is only ever read by the single owning user for a single period, so an indexed
  `GROUP BY category_id` on demand is cheaper than maintaining another table.

### Storing dates: calendar date, not instant

- **Decision**: `entry_date` is a SQL `DATE` mapped to `java.time.LocalDate`. No time, no zone.
- **Rationale**: The spec's timezone edge case — an entry recorded near midnight on the last day of
  a month landing in exactly one period — dissolves entirely if the date carries no time component.
  The date a user assigns to a transaction is a fact about their calendar, not a point on a
  timeline. Period membership becomes `entry_date BETWEEN period_start AND period_end`, with no
  conversion and no server-timezone dependency. The user's stored timezone is then only used by
  clients to default "today" correctly, which is exactly the scope it deserves.
- **Alternatives considered**: `TIMESTAMP` in UTC with per-query timezone conversion — rejected;
  it makes period boundaries a function of a mutable user setting, so changing your timezone would
  silently move historical entries between months and rewrite past scores.

### Money representation

- **Decision**: `amount_minor BIGINT` plus `currency CHAR(3)`, wrapped in a `Money` record. No
  floating point anywhere in the stack.
- **Rationale**: Directly mandated by constitution Principle III. `BIGINT` minor units holds any
  realistic personal-finance amount with exact arithmetic. A `Money` value type keeps currency
  attached to the number so that a mismatched-currency addition is a compile-time or fail-fast
  error rather than a silent wrong total.
- **Alternatives considered**: `DECIMAL(19,4)` — the conventional choice and perfectly correct
  numerically, but it does not satisfy the constitution's explicit minor-units requirement and it
  invites accidental `double` conversion at the JDBC boundary. JSR-354 `MonetaryAmount` — rejected
  as a heavyweight dependency for a single-currency-per-user v1.

### Score precision and ranking stability

- **Decision**: Store the proportion in **basis points** (`proportion_bp INT`, 0–10000). Expose the
  score to clients as an integer 0–100. Rank the comparison view by `proportion_bp`, not by the
  displayed score.
- **Rationale**: Two friends at 30.4% and 30.6% both display as a score of 30. Ranking on the
  displayed integer makes their relative order arbitrary and unstable across requests; ranking on
  basis points makes it deterministic and correct while the UI stays as simple as the spec
  describes. Costs one `INT` column.
- **Alternatives considered**: Storing only the integer score — rejected for the tie instability.
  Storing a `DECIMAL` proportion — unnecessary precision for a ratio that is already an
  approximation of self-reported figures.

### Reconciliation tolerance

- **Decision**: A period is flagged when `gap > max(10% of money_in, absolute_floor)`, where both
  the percentage and the floor are externalised configuration. Default floor: 10,000 minor units
  (100.00 in the user's currency).
- **Rationale**: The spec deliberately left both tunable. The percentage alone misbehaves at small
  incomes — someone with 5,000 of recorded income gets flagged over a 500 discrepancy that is
  probably a forgotten cash gift. The floor suppresses that noise. Externalising both means product
  can tune the false-positive rate against SC-009 and SC-010 without a code change.
- **Open item for product**: the floor is currency-relative and the default assumes a currency where
  100.00 is a meaningful but not trivial sum. Confirm before launch in any additional currency.
- **Alternatives considered**: Percentage only — rejected for small-income false positives. Fixed
  absolute only — rejected because it flags nothing for high earners.

### Streak computation

- **Decision**: Compute on read from `period_summary` rows with a **bounded 24-month lookback**.
  A streak that would extend past 24 months is reported as 24.
- **Rationale**: Storing an incrementally-maintained streak on each row breaks under backdated
  edits, which the spec explicitly requires to be supported — correcting a month two years ago would
  need the entire forward chain rewritten. Reading 24 rows per person is cheap: the comparison view
  for 51 people is one query returning at most 1,224 small rows, all covered by the primary index.
- **Cap is declared, not silent**, per the constitution: the 24-month bound is documented in the API
  contract and surfaced in the response, so a client never presents a truncated streak as complete.
- **Alternatives considered**: Materialised streak column — rejected on backdated-edit invalidation.
  Unbounded lookback — rejected as an unbounded query, which the constitution forbids outright.

### Authorisation enforcement point

- **Decision**: Every repository finder takes the owning user's ID as a parameter. There is no
  `findById(id)` on any owned entity. Score visibility additionally passes through a friendship
  check before a `period_summary` row belonging to another user can be read.
- **Rationale**: Constitution Principle IV requires authorisation at the data-access layer, not
  only at the route layer, and this is the mechanical way to guarantee it: a query that could return
  another user's row cannot be written, because the method to write it does not exist. It also
  makes the requirement testable — a repository test asserting that a foreign ID returns empty is
  trivial, whereas asserting a controller checked something is not.
- **Alternatives considered**: `@PreAuthorize` on service methods — rejected as the sole mechanism;
  it is a route-layer check wearing a service-layer costume and is bypassed by any new call path.
  Hibernate filters — rejected as implicit; a developer reading the repository would not see the
  constraint.

### Authentication

- **Decision**: Short-lived JWT access tokens (15 minutes) plus opaque, database-backed refresh
  tokens that can be revoked. Per-account lockout state stored in MySQL.
- **Rationale**: Stateless access tokens satisfy Principle V's requirement that no request state
  live in the process. The revocable refresh token covers what a pure-JWT scheme cannot — blocking,
  account deletion, and credential compromise all need to invalidate a live session, and a
  15-minute access-token window bounds the exposure. Lockout counters in MySQL are shared across
  instances, so brute-force protection is exact rather than per-instance approximate.
- **Alternatives considered**: Server-side sessions — rejected; it puts state back in the process
  or requires a session store. Long-lived JWTs with no refresh — rejected; unrevocable.

### Idempotency

- **Decision**: An `Idempotency-Key` request header on entry creation, recorded in an
  `idempotency_record` table with a unique constraint on `(user_id, key)` and a 24-hour retention
  window. A replayed key returns the original response.
- **Rationale**: FR-010 requires that a retried submission not duplicate an entry, and the spec's
  duplicate-submission edge case describes exactly a client retry after timeout. The unique
  constraint makes the guarantee the database's job rather than the application's, so it holds
  under concurrent retries.
- **Alternatives considered**: Content-hash deduplication — rejected; two genuinely identical
  coffee purchases on the same day are legitimate and must both be recorded.

### Friendship storage and the simultaneous-request race

- **Decision**: One row per pair with canonically ordered `(user_a_id, user_b_id)` where
  `user_a_id < user_b_id`, a unique constraint on that pair, plus `initiated_by_user_id`, `status`,
  and `blocked_by_user_id`.
- **Rationale**: Canonical ordering plus a unique constraint makes the spec's simultaneous-mutual-
  request edge case resolve at the database level — the second insert fails, and the loser's request
  is interpreted as an acceptance. Storing the direction separately keeps the asymmetric facts
  (who asked, who blocked) intact while the symmetric fact (these two are connected) has exactly one
  row.
- **Alternatives considered**: Two directed rows per friendship — rejected; it makes every state
  transition a two-row update and admits inconsistent halves.

### Testing against real MySQL

- **Decision**: Testcontainers running MySQL 8.4 for all repository and integration tests. No H2.
- **Rationale**: The constitution requires integration tests for schema migrations and forbids
  flaky tests. H2's MySQL compatibility mode diverges on exactly the things this schema depends on
  — index behaviour, date handling, and constraint semantics — so a green H2 suite would provide
  false confidence about Flyway migrations that have never run against the real engine.
- **Alternatives considered**: H2 in MySQL mode — rejected on dialect drift. A shared CI database —
  rejected; concurrent test runs would interfere, producing precisely the flakiness the
  constitution requires be eliminated within a day.

## Resolved unknowns

No `NEEDS CLARIFICATION` markers remain from the Technical Context. The three items that required
verification rather than assumption — the current Java LTS, whether Maven 4 had reached GA, and
which MySQL track to target — are resolved above with their sources below.

One item is deferred to product rather than engineering: the **absolute floor for the
reconciliation tolerance** has a working default of 10,000 minor units but is currency-relative and
should be confirmed against real data once there is any.

## Sources

- [JDK 25](https://openjdk.org/projects/jdk/25/) — LTS, GA 2025-09-16
- [JDK 26](https://openjdk.org/projects/jdk/26/) — non-LTS, GA 2026-03-17, support ends 2026-09
- [Spring Boot releases](https://github.com/spring-projects/spring-boot/releases) and
  [Spring Boot 4.1 release highlights](https://spring.io/projects/release-highlights/)
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) — starter renames
- [Modularizing Spring Boot](https://spring.io/blog/2025/10/28/modularizing-spring-boot/) — auto-configuration modularisation
- [Spring Framework 7.0 GA](https://spring.io/blog/2025/11/13/spring-framework-7-0-general-availability/) and
  [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)
- [Spring Framework 7 and Spring Boot 4 Deliver API Versioning, Resilience, and Null-Safe Annotations — InfoQ](https://www.infoq.com/news/2025/11/spring-7-spring-boot-4/)
- [Maven Releases History](https://maven.apache.org/docs/history.html) and
  [Apache Maven 4.0.0-rc-5 Release Notes](https://maven.apache.org/docs/4.0.0-rc-5/release-notes.html) — Maven 4 still pre-GA
- [MySQL Releases: Innovation and LTS](https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html) and
  [MySQL — endoflife.date](https://endoflife.date/mysql)
