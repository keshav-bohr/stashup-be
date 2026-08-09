# Implementation Plan: Stash Score Tracker

**Branch**: `001-stash-score-tracker` | **Date**: 2026-08-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-stash-score-tracker/spec.md`

## Summary

A stateless Spring Boot backend that records self-reported financial entries, derives a 0–100
stash score from the proportion of income a user converts into savings and investments, checks each
period against itself for completeness, and exposes score-only comparison between accepted friends.

The technical core is one decision: **period totals and scores are materialised into a
`period_summary` table and recomputed synchronously inside the same transaction as the entry
mutation that invalidated them.** A monthly aggregate for a single user is a millisecond-scale
indexed query, so this stays well inside the write budget while turning the read paths — score,
summary, and the 51-row friend comparison — into single indexed lookups rather than fan-out
aggregations. Yearly figures are derived by summing the twelve monthly rows, never by averaging
monthly scores.

Two secondary decisions carry most of the remaining risk out of the design: entry dates are stored
as **calendar dates with no time component**, which eliminates timezone-boundary ambiguity from
period membership entirely; and monetary amounts are stored as **integer minor units with an
explicit currency**, as the constitution requires, with the score proportion held in basis points
so that ranking stays stable when two friends round to the same displayed score.

## Technical Context

**Language/Version**: Java 25 (LTS, GA 2025-09-16). Not Java 26 — it is GA but non-LTS and reaches
end of support in September 2026, one month from this plan's date. Language level 25, toolchain
pinned via Maven Enforcer. Local JDK confirmed at 25.0.2.

**Primary Dependencies**: Spring Boot 4.1.x (latest GA, 2026-06-10) on Spring Framework 7 —
`spring-boot-starter-webmvc` (renamed from `-web` in Boot 4), `-data-jpa`, `-security`,
`-validation`, `-actuator`, plus the matching `-test` starter per module. Flyway for migrations,
HikariCP (Boot default) for pooling, Micrometer + Micrometer Tracing for observability,
springdoc-openapi for schema generation, Bucket4j for rate limiting, Testcontainers for tests.
Spring Framework 7's native API versioning, `@Retryable`, and JSpecify null-safety annotations are
used rather than third-party equivalents.

**Storage**: MySQL 8.4 LTS (8.4.11). Not the 9.x/calendar-versioned innovation track — LTS is the
correct target for a system of record holding personal financial data. Local MySQL confirmed at
8.4.6. Schema managed forward-only by Flyway.

**Testing**: JUnit 5 + AssertJ, MockMvc for per-endpoint contract tests, Testcontainers MySQL 8.4
for integration and repository tests (no H2 — dialect drift would let SQL bugs through), JaCoCo
enforcing the constitution's 80% overall / 90% sensitive-module gates, and a seeded latency
regression suite for the paths named in the performance budget.

**Target Platform**: Linux server, containerised. Stateless JVM processes behind a load balancer.
Virtual threads enabled (`spring.threads.virtual.enabled=true`) — a blocking JDBC workload on
Java 25 is close to the ideal case for them.

**Project Type**: Web service (REST backend). No frontend in this repository.

**Performance Goals**: Constitution defaults govern and are stricter than the spec's SC-004:
read endpoints p95 < 200 ms / p99 < 500 ms, write endpoints p95 < 500 ms. The comparison view for
50 friends and the yearly summary over 3 years of history are the two paths held to this explicitly.

**Constraints**: Every query scoped by owning user at the repository layer, not the controller.
No unbounded collections. Idempotent writes. Forward-only migrations. Amounts never rendered into
logs.

**Scale/Scope**: 10,000 concurrent users viewing scores (SC-013), a heavy user at ~3 years and tens
of thousands of entries, up to a few hundred friends per user. 8 resource groups, ~30 endpoints,
9 tables.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

**Initial evaluation — PASS with two tracked deviations.**

| Principle | How this design satisfies it | Verdict |
|-----------|------------------------------|---------|
| **I. Code Quality** | Spotless (formatting), Checkstyle (cyclomatic complexity ≤ 10, file length ≤ 500), Error Prone + NullAway over JSpecify annotations for strict nullness, `-Xlint:all -Werror` on javac. All wired as build-failing, not advisory. No Lombok — Java records and the compiler cover the need without a bytecode-manipulating dependency. | PASS |
| **II. Test-First** | Contract test per endpoint via MockMvc asserting request schema, response schema, and every documented status; integration tests on real MySQL via Testcontainers; JaCoCo gates at 80% / 90% for `security`, `score`, `period`, `friendship`. Tests precede implementation per the Red-Green-Refactor requirement. | PASS |
| **III. Consistent API** | RFC 9457 `ProblemDetail` as the single error envelope, extended with a stable `code` and the `correlationId`. Instants are ISO 8601 UTC. Money is integer minor units + explicit currency. IDs are UUIDv7 exposed as opaque strings. Spring Framework 7 API versioning under `/api/v1`. OpenAPI generated by springdoc from the running application and diffed in CI. | PASS — see note below on dates |
| **IV. Security By Default** | Spring Security 7 with `denyAll` as the default and public endpoints individually annotated and justified. Authorisation enforced in the repository layer: no finder exists that is not scoped by owning user. Bean Validation on every request body. Secrets from environment only. Log redaction filter for amounts and PII. OWASP dependency-check failing the build on high/critical. | PASS with deviation (rate limiting) |
| **V. Scalability** | Stateless processes, JWT access tokens, no session or local disk state. Idempotency keys on every external write. HikariCP pooling. Every growing-table query index-backed. Keyset pagination with an enforced max page size of 100. | PASS with justification (synchronous recompute) |
| **VI. Performance** | Budgets stated above and asserted by a seeded latency suite in CI with a 20% regression gate. Materialised summaries keep the comparison path to one indexed query. Micrometer metrics, Micrometer Tracing, and structured JSON logs carry the correlation ID end to end. | PASS |

**Note on Principle III and dates.** The constitution requires all timestamps to be ISO 8601 UTC.
`entry_date` is deliberately *not* a timestamp — it is a calendar date (`DATE` / `LocalDate`) with
no time component, because the date a user assigns to a transaction is a fact about their calendar,
not an instant. All true instants (`created_at`, `acknowledged_at`, token expiry) are ISO 8601 UTC
as required. This is a clarification of the rule, not an exception to it.

**Tracked deviations** are recorded in Complexity Tracking below.

**Post-Phase 1 re-evaluation — PASS, unchanged.** The design artifacts introduced no new
violations. The data model tightened Principle IV compliance by making user scoping a schema-level
property (every owned table carries `user_id` and every index leads with it), and the contract
tightened Principle III by fixing one error envelope across all 30 endpoints. The two deviations
below are the complete set.

## Project Structure

### Documentation (this feature)

```text
specs/001-stash-score-tracker/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── openapi.yaml
│   └── README.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
pom.xml
src/main/java/com/stashup/
├── StashUpApplication.java
├── config/               # ApplicationProperties, TimeConfig, SecurityConfig
├── controller/           # 9 @RestControllers — auth, user, category, entry, summary,
│                         # score, reconciliation, friendship, comparison
├── service/              # Business logic — 20 classes including the score and streak
│                         # calculators, entry validator, and reconciliation prompts
├── repository/           # 8 Spring Data interfaces, every finder scoped by owning user
├── entity/               # 8 JPA entities
├── dto/                  # Request/response records, one Dtos holder per resource
├── domain/               # Framework-free value objects and enums: Money, EntryType,
│                         # Direction, Completeness, ScoreBand, PeriodRef, PeriodTotals
├── exception/            # ErrorCode, ApiException, GlobalExceptionHandler
├── security/             # @CurrentUserId and its argument resolver
├── web/                  # Correlation filter, rate limiting, keyset pagination
└── util/                 # UuidV7

src/main/resources/
├── application.yml
├── application-local.yml
└── db/migration/         # Flyway forward-only migrations

src/test/java/com/stashup/
├── contract/             # HTTP-level tests through the real filter chain
├── integration/          # Real MySQL, cross-layer flows, privacy assertions
├── unit/                 # Scoring, reconciliation, money arithmetic, streak logic
└── support/              # MySqlTestBase
```

**Structure Decision**: A single Maven module, packaged by layer — the conventional Spring
arrangement, chosen for familiarity so any Spring developer can navigate it without a map. A
multi-module build was rejected as unjustified complexity under the constitution's simplicity
rule: there is one deployable, and module boundaries would enforce a separation the packages
already express.

`domain/` holds framework-free value objects and enums, so the scoring and money rules are
testable without Spring — `ScoreCalculator` and `Money` have no Spring dependency at all.

The trade-off this layout costs us is worth naming, because it affects how the constitution's
Principle IV is enforced. Package-by-feature would have let a reviewer answer "does anything
outside the friendship code read another user's score?" by reading one directory. Layered, that
question spans `controller/`, `service/`, and `repository/`. Two things compensate:
`FriendVisibilityService` is the single gate every cross-user score read must pass through, and
`AmountLeakageIT` asserts the absence of leaks from the outside by sweeping serialised responses.
The JaCoCo 90% gate likewise moved from a package rule to a named-class rule, so adding a
sensitive class is now a deliberate edit to `pom.xml` rather than something inherited from a
package name.

## Complexity Tracking

> Filled because the Constitution Check identified two deviations requiring justification.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| **Principle IV — rate limiting is per-instance, not global.** The constitution requires every state-changing endpoint to be rate-limited. Bucket4j with in-memory buckets enforces the limit per JVM, so the effective cluster-wide limit is *N × configured* with N instances. | A globally accurate limiter needs shared state — Redis or equivalent — which is a new piece of infrastructure, a new failure mode, and a new operational burden for a v1 with no users. The per-account brute-force protection that actually matters for security is *not* approximate: failed login attempts and lockout state live in MySQL and are therefore correct across all instances. | Redis was rejected for v1 as infrastructure ahead of need. Doing nothing was rejected because it violates the principle outright. The accepted middle is: exact where it protects credentials, approximate where it protects capacity. Upgrade path is a Redis-backed `ProxyManager` behind the same Bucket4j interface, with no call-site changes. |
| **Principle V — score recomputation is synchronous, not queued.** The constitution requires work exceeding 2 seconds, or work that can fail independently of the caller, to be moved to a queue. Recomputation runs inside the entry-mutation transaction. | The unit of work is a single `GROUP BY` over one user's one month, index-backed on `(user_id, entry_date)` — sub-millisecond at realistic volumes and bounded by a single user's monthly entry count, not by table size. Introducing a queue would add a broker, a consumer, an at-least-once delivery contract, and a window where a user sees a stale score immediately after saving an entry. | An async queue was rejected as failing the simplicity rule for work three orders of magnitude inside the threshold, and as actively worse for the user: SC-005 permits one minute of staleness but zero staleness is better, and doing it in-transaction makes the summary trivially consistent with the entries rather than eventually consistent. Revisit if a single user's monthly entry count ever makes the aggregate exceed the write budget. |
