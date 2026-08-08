# Specification Quality Checklist: Stash Score Tracker

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-08
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`

### Validation notes (iteration 1)

- Non-clarification items all passed on first review; no spec rewrite was required.
- 3 [NEEDS CLARIFICATION] markers raised on score basis (FR-014), recommendation scope (FR-029),
  and friend visibility (FR-026).

### Validation notes (iteration 2 — after clarification session 2026-08-08)

All 16 items now pass. Changes made in response to the answers:

| Answer | Spec change |
|--------|-------------|
| Score is proportion-based, 0–100 | FR-014 states the formula in plain terms with the user's own worked example; FR-015 added to forbid income size influencing the score; User Story 2 scenario 2 encodes the 100/10 vs 10/5 case; SC-007 added to verify income-independence across the scale |
| Recommendations deferred | User Story 4 removed; former FR-029–FR-034 removed; `Recommendation` entity removed; former SC-007 (recommendation action rate) removed; Out of Scope section added recording the deferral and its rationale |
| Score and band visible to friends only | FR-027 rewritten to make non-friends see nothing, explicitly covering pending requesters, blocked users, former friends, and search results; FR-028 keeps amounts private even between friends; User Story 3 scenario 3 and the friendship-revoked-mid-view edge case added; SC-006 extended to cover non-friend score leakage |

- Functional requirements renumbered contiguously FR-001 → FR-034 after the removals.
- A `Clarifications` section was added to the spec recording all three questions and answers.
- One accepted risk is documented rather than solved: self-reported income can be understated to
  inflate a score. Called out in Assumptions and in the Edge Cases score-gaming entry so it reaches
  planning as a known limitation rather than resurfacing as a defect.

### Validation notes (iteration 3 — income integrity, 2026-08-08)

The accepted risk carried out of iteration 2 was reopened and partially closed. All 16 items still
pass. Changes:

- **New User Story 3 — "Keep the score honest" (P2)**, independently testable: seed a period whose
  outflow exceeds income, confirm the score returns flagged with the gap, then acknowledge a
  drawdown and confirm the state clears without the score value moving. Friends/comparison demoted
  to User Story 4 (still P3).
- **New requirement group FR-023 → FR-029, "Data completeness and score integrity"**: reconciliation
  gap computation, tolerance-based flagging, non-accusatory prompt with exactly two resolutions,
  drawdown acknowledgment, three-state completeness on every score, re-evaluation on entry edits,
  and an explicit rule that completeness never alters the score value.
- **FR-034 added**: the comparison view now shows change-since-last-period and streak of complete
  months alongside rank, so consistency is visible next to absolute standing and a fabricated
  number wins less.
- **FR-035 added**: only `complete` scores are rankable; incomplete periods appear as such to
  friends with no number and no rank.
- **FR-037 extended** to keep the reconciliation gap private, like every other amount.
- **New entity** `Drawdown Acknowledgment`; `Period Summary` gains the reconciliation gap;
  `Stash Score` gains completeness state and period-over-period change.
- **SC-008, SC-009, SC-010 added** — resolution rate, the share of prompts resolved by recording
  genuinely forgotten income (evidence the check serves completeness over policing), and a ceiling
  on users who find the prompt accusatory. Renumbered to SC-014.
- **Out of Scope extended** with income verification and behavioural anomaly detection, both
  deliberately deferred.

Residual accepted risk, narrowed and still documented: a user who under-reports income *and*
correspondingly under-reports expenses produces a self-consistent ledger no internal check can
detect. Closing it requires verification, which is out of scope.

- Functional requirements renumbered contiguously FR-001 → FR-043; success criteria SC-001 → SC-014.
