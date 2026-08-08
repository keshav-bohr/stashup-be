<!--
Sync Impact Report
==================
Version change: (unfilled template) → 1.0.0
Bump rationale: Initial ratification. All placeholder tokens replaced with concrete
governance content; no prior ratified version existed.

Modified principles:
  - [PRINCIPLE_1_NAME] → I. Code Quality Is Non-Negotiable
  - [PRINCIPLE_2_NAME] → II. Test-First Development (NON-NEGOTIABLE)
  - [PRINCIPLE_3_NAME] → III. Consistent API Experience
  - [PRINCIPLE_4_NAME] → IV. Security By Default
  - [PRINCIPLE_5_NAME] → V. Scalability Through Statelessness
  - (added)            → VI. Performance Is A Budget, Not A Hope

Added sections:
  - Core Principles (6 principles; template shipped 5 slots)
  - Quality & Operational Standards (was [SECTION_2_NAME])
  - Development Workflow (was [SECTION_3_NAME])
  - Governance

Removed sections: none

Deferred items:
  - TODO(RATIFICATION_DATE): Recorded as 2026-08-08, the date this constitution was
    first authored. Replace if the project formally adopted governance on an earlier date.
-->

# StashUp Backend Constitution

## Core Principles

### I. Code Quality Is Non-Negotiable

Every merged change MUST satisfy all of the following:

- Formatter and linter run in CI with zero warnings tolerated; warnings are build failures,
  not advisories. Suppressions MUST carry an inline comment naming the reason.
- Static type checking MUST be enabled in strict mode. Escape hatches (`any`, untyped casts,
  reflection over unknown shapes) MUST be justified in the PR description.
- Public functions, exported types, and module boundaries MUST be self-documenting through
  naming; comments explain *why*, never *what*.
- Cyclomatic complexity above 10 in a single function, or a file exceeding 500 lines, MUST be
  refactored or explicitly justified during review.
- Dead code, commented-out code, and unused dependencies MUST be deleted, not archived in place.

**Rationale**: A backend accretes callers it cannot see. Consistency and mechanical enforcement
keep review attention on design rather than on style arguments, and keep the cost of the tenth
change equal to the cost of the first.

### II. Test-First Development (NON-NEGOTIABLE)

- Tests MUST be written before the implementation they cover, MUST be observed failing, and only
  then made to pass. Red-Green-Refactor is the required cycle.
- Every API endpoint MUST have a contract test asserting request schema, response schema, and
  status codes for both success and each documented failure mode.
- Integration tests are REQUIRED for: new service contracts, contract changes, inter-service
  communication, database schema migrations, and shared data schemas.
- Line coverage MUST be at least 80% overall and at least 90% for modules handling
  authentication, authorization, payments, or persistence. Coverage MUST NOT regress between
  merges.
- Tests MUST be deterministic. A flaky test MUST be fixed or deleted within one working day of
  detection; quarantining without a tracked fix is prohibited.
- External dependencies MUST be faked or contract-tested, never reached over the network from
  unit tests.

**Rationale**: Tests written after the fact encode the implementation's bugs as expected
behavior. Writing them first forces the interface to be designed from the caller's side.

### III. Consistent API Experience

The API is the product surface. Consumers MUST be able to predict behavior from one endpoint
after learning another.

- Resource naming, pluralization, casing, pagination, filtering, and sorting conventions MUST be
  uniform across every endpoint. A convention is defined once and applied everywhere.
- Errors MUST use a single machine-readable envelope containing a stable error code, a
  human-readable message, and a correlation ID. Error codes MUST NOT be reworded in ways that
  break consumers matching on them.
- All timestamps MUST be ISO 8601 UTC. All monetary values MUST carry an explicit currency and
  use integer minor units. All identifiers MUST be opaque strings to the client.
- Breaking changes MUST be shipped behind a new API version. The prior version MUST remain
  supported for at least 90 days after deprecation is announced.
- Every endpoint MUST be documented in a machine-readable API schema, and that schema MUST be
  generated from or validated against the running code in CI.

**Rationale**: Inconsistency is a tax paid by every client integration forever. Uniformity is
cheaper to enforce at authoring time than to retrofit after adoption.

### IV. Security By Default

- Every endpoint MUST be authenticated and authorized unless it is explicitly annotated as
  public, and each public endpoint MUST be justified in review.
- Authorization MUST be enforced at the data-access layer, not only at the route layer. A query
  MUST NOT be able to return records the caller cannot see.
- All input MUST be validated against a schema at the boundary. Unvalidated input MUST NOT reach
  business logic or persistence.
- Secrets MUST come from a secrets manager or environment injection. Secrets, tokens, keys, and
  credentials MUST NEVER appear in source, logs, error messages, or test fixtures.
- Personally identifiable information and credentials MUST be redacted in logs. Data MUST be
  encrypted in transit (TLS 1.2+) and at rest.
- Dependency vulnerability scanning MUST run in CI. Critical and high severity findings block
  merge; medium findings MUST be triaged within 7 days.
- Every state-changing endpoint MUST be rate-limited, and authentication endpoints MUST
  additionally apply per-account lockout or backoff.

**Rationale**: Security added after a breach is remediation, not design. Defaults decide
outcomes, because the default is what ships when someone is in a hurry.

### V. Scalability Through Statelessness

- Application processes MUST be stateless and horizontally scalable. Any state that outlives a
  request MUST live in a datastore, cache, or queue — never in process memory or local disk.
- Work exceeding 2 seconds, or work that can fail independently of the caller, MUST be moved to
  an asynchronous job or queue rather than held open in a request.
- Every write operation exposed to external callers or retried by a queue MUST be idempotent,
  keyed by a client-supplied or derived idempotency key.
- Database access MUST use connection pooling, and every query against a growing table MUST be
  index-backed. Unbounded queries and N+1 access patterns are prohibited.
- All list endpoints MUST be paginated with an enforced maximum page size. No endpoint may return
  an unbounded collection.

**Rationale**: Scaling is mostly the absence of hidden coupling. Statelessness and idempotency
are what make "add another instance" and "retry it" safe answers.

### VI. Performance Is A Budget, Not A Hope

- Default service budgets, which apply unless a feature specification defines stricter ones:
  read endpoints p95 under 200 ms and p99 under 500 ms; write endpoints p95 under 500 ms;
  asynchronous jobs p95 under 30 seconds, measured server-side excluding client network time.
- Every feature specification MUST state its expected request volume and its latency budget.
  A feature without a stated budget is incomplete.
- Performance-sensitive paths MUST have a benchmark or load test in CI. A regression greater than
  20% against the recorded baseline blocks merge.
- Structured logging, metrics, and distributed tracing MUST be emitted for every request, carrying
  the correlation ID required by Principle III.
- Optimization MUST be driven by measurement. Speculative optimization that increases complexity
  without a profile or benchmark demonstrating the gain MUST be rejected.

**Rationale**: Latency budgets stated before implementation are design constraints; measured after
release they are incidents. Numbers make the tradeoff reviewable.

## Quality & Operational Standards

- **Observability**: Every service MUST expose a health endpoint, a readiness endpoint, and
  metrics in a scrapeable format. Every log line MUST be structured and include the correlation
  ID, so a single request is traceable end to end.
- **Configuration**: All configuration MUST come from the environment. The service MUST fail fast
  at startup on missing or malformed required configuration rather than degrading at runtime.
- **Data migrations**: Migrations MUST be forward-only, reversible or explicitly documented as
  irreversible, and tested against a production-shaped dataset before deployment.
- **Dependencies**: Adding a third-party dependency MUST be justified in review against the cost
  of implementing the capability directly. Dependency versions MUST be pinned and lockfiles
  committed.
- **Error handling**: Errors MUST be handled or propagated with context. Swallowing an exception
  without logging and without a comment explaining why is prohibited.

## Development Workflow

- **Branching**: Work happens on branches. Direct commits to the default branch are prohibited.
- **Review**: Every change requires at least one approving review. Changes touching
  authentication, authorization, payments, or data migrations require two, one of which MUST come
  from a maintainer.
- **Quality gates**: CI MUST pass before merge — formatter, linter, strict type check, unit
  tests, integration tests, contract tests, coverage thresholds, and vulnerability scan. Gates
  MUST NOT be bypassed; a broken gate is fixed, not skipped.
- **Commits**: Commit messages MUST describe intent, not mechanics, and MUST reference the
  feature or issue they serve.
- **Definition of done**: Code merged, tests passing, API schema updated, observability in place,
  latency budget verified, and documentation reflecting the change.
- **Incidents**: Any production incident MUST produce a blameless postmortem and at least one
  regression test or automated check that would have caught it.

## Governance

This constitution supersedes all other development practices, conventions, and habits. Where a
tool default, a style guide, or prior code conflicts with this document, this document wins.

- **Amendments**: Amendments MUST be proposed as a pull request modifying this file, MUST state
  the rationale and the migration path for existing code, and MUST be approved by a project
  maintainer. Amendments take effect on merge.
- **Versioning**: This constitution follows semantic versioning. MAJOR for removing or
  incompatibly redefining a principle or governance rule; MINOR for adding a principle or
  materially expanding guidance; PATCH for clarifications and non-semantic refinements.
- **Compliance review**: Every pull request review MUST verify compliance with these principles.
  Reviewers MUST cite the specific principle when requesting changes on constitutional grounds.
- **Justified exceptions**: A principle may be violated only with an explicit, written
  justification recorded in the pull request describing what was violated, why the alternative was
  worse, and what would need to change to come back into compliance. Undocumented violations MUST
  be reverted.
- **Complexity**: Complexity MUST be justified. When a simpler design satisfies the requirement,
  the simpler design MUST be chosen.
- **Runtime guidance**: Agent- and contributor-facing development guidance lives in `CLAUDE.md`
  at the repository root and MUST remain consistent with this constitution. Where the two
  conflict, this constitution governs and `CLAUDE.md` MUST be corrected.

**Version**: 1.0.0 | **Ratified**: 2026-08-08 | **Last Amended**: 2026-08-08
