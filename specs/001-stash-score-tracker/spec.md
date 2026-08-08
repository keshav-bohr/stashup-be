# Feature Specification: Stash Score Tracker

**Feature Branch**: `001-stash-score-tracker`

**Created**: 2026-08-08

**Status**: Draft — clarified 2026-08-08

**Input**: User description: "Create an application where the user will be able to add their expenses, savings, investments, other deductions, etc that will help them to track the expenses. The user will be given a stash score that will be purely based on the amount of savings/investments they did during an entire month or a year. The user can add friends and compare their stash score with them and can get more ideas and recommendation as to how they can contribute more in their savings part"

## Clarifications

### Session 2026-08-08

- **Q: Should the stash score measure the savings rate, the absolute amount stashed, or a
  composite?** → A: The **proportion** of money in that was stashed, on a 0–100 scale. Someone
  earning 100 and stashing 10 (10%) scores below someone earning 10 and stashing 5 (50%). This
  puts every user on the same baseline regardless of income, and makes income a mandatory tracked
  entry type.
- **Q: What is the scope of the recommendation engine?** → A: **Deferred out of scope.** Ideas for
  saving more are expected to travel informally between friends — a user sees a friend's high score
  and asks them directly what they did. The product surfaces the score that starts that
  conversation; it does not generate advice. See Out of Scope.
- **Q: How much detail can friends see about each other?** → A: **Score and band only, and only
  between accepted friends.** Non-friends see nothing at all — no score, no band. No amount, entry,
  category, or income is ever visible to anyone but the owner.

### Session 2026-08-08 (follow-up — self-reported income integrity)

- **Q: How do we deal with a user understating their income to inflate their score?** → A: Address
  it through **reconciliation, not verification**. The system already holds both sides of the
  ledger, so it compares recorded money in against recorded outflow and flags periods that do not
  balance. A gap is surfaced as a non-accusatory prompt to add missing income or acknowledge a
  drawdown from prior balances; unreconciled periods are excluded from friend rankings. This
  primarily serves honest users with incomplete data — deliberate understatement is additionally
  made costly because concealing it requires also understating expenses, which destroys the expense
  tracking the user came for. Verification (payslips, bank linking) and behavioural anomaly
  detection remain out of scope.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Record and review money movements (Priority: P1)

A person signs up, sets the month they want to start tracking, and records the money that moves
through their life: what came in, what they spent, what they set aside as savings, what they put
into investments, and what was deducted before they ever saw it (tax, provident fund, loan
instalments, insurance premiums). Each entry carries an amount, a date, a category, and an
optional note. They can list, filter, edit, and delete their entries, and see a summary for any
month or year showing totals per type and per category.

**Why this priority**: Nothing else in the product exists without this data. The score and the
comparison are both derived from it. On its own this is already a usable expense tracker, which
makes it a legitimate standalone MVP.

**Independent Test**: Create an account, add entries of each type across two different months,
then open the monthly and yearly summaries and confirm the totals, category breakdowns, and
filtered lists match the entries recorded. Delivers value as a working personal finance tracker
even if no other story ships.

**Acceptance Scenarios**:

1. **Given** a signed-in user with no entries, **When** they record an expense of 1,200 in
   "Groceries" dated this month, **Then** the entry appears in their entry list and this month's
   expense total is 1,200.
2. **Given** a user with entries across January and February, **When** they request the January
   summary, **Then** only January entries are included in the totals.
3. **Given** a user viewing an existing entry, **When** they change its amount and save,
   **Then** the affected period summaries reflect the new amount and the old amount is no longer
   counted anywhere.
4. **Given** a user deletes an entry, **When** they view the summary for that entry's period,
   **Then** the totals exclude the deleted entry.
5. **Given** a user attempts to record an entry with a negative or zero amount, **When** they
   submit it, **Then** the system rejects it with a message explaining that amounts must be
   greater than zero.
6. **Given** a user records an entry dated in the future, **When** they submit it, **Then** the
   system rejects it with a message explaining that entries cannot be dated in the future.

---

### User Story 2 - See my stash score (Priority: P2)

Based on the entries recorded, the user is given a **stash score** for a chosen month or for a
full year. The score is the share of the money that came in during that period which the user
converted into savings and investments, expressed on a 0–100 scale — so 50 means half of what came
in was stashed. Alongside the number, the user sees the inputs that produced it (money in, amount
stashed, and the resulting proportion), the score band it falls into, and how it compares to their
own previous periods.

**Why this priority**: The score is the product's identity and the reason a user prefers this over
a plain expense tracker. It depends on P1 data but is independently demonstrable once entries
exist.

**Independent Test**: Seed a user with a known set of entries for one month, request the score for
that month, and verify the returned score, its component inputs, and its band match the documented
scoring rules. Then request the yearly score and verify it aggregates the months correctly.

**Acceptance Scenarios**:

1. **Given** a user whose recorded money-in for March is 100,000 and whose net savings plus
   investments for March total 30,000, **When** they request their March stash score, **Then** the
   score is 30 and the response shows both input figures and the 30% proportion.
2. **Given** user A earned 100 and stashed 10 in a period, and user B earned 10 and stashed 5 in
   the same period, **When** both scores are computed, **Then** A scores 10 and B scores 50 — the
   lower earner scores higher because they stashed a greater share.
3. **Given** a user with no entries for a requested period, **When** they request that period's
   score, **Then** the system returns an explicit "not enough data" state rather than a score of
   zero.
4. **Given** a user with recorded money-in but no savings or investments, **When** they request
   their score, **Then** the score is 0 and the response distinguishes this from "not enough data".
5. **Given** a user with scores for the previous three months, **When** they request the current
   month's score, **Then** the response includes the direction and size of the change versus the
   prior month.
6. **Given** a user adds a backdated savings entry into a closed month, **When** they request that
   month's score again, **Then** the score is recalculated to include the new entry.
7. **Given** a user requests a yearly score for a year in which only four months have data,
   **When** the score is returned, **Then** the response states how many months contributed to it.

---

### User Story 3 - Keep the score honest (Priority: P2)

Because every figure is self-reported, a user can inflate their score — deliberately or by
accident — simply by not recording all the money that came in. The system checks each period
against itself: the money recorded as coming in should roughly account for the money recorded as
going out. When outflow substantially exceeds money in, the user is told plainly that the period
does not add up, shown the size of the gap, and offered two ways to resolve it — record the missing
income, or confirm they were drawing on savings held before this period. Until a period is
resolved, its score is still shown to the user, but it is marked incomplete and does not enter any
friend ranking.

**Why this priority**: Shares P2 with the score itself because a comparison built on unchecked
self-reporting is not worth comparing. In practice this feature spends most of its life helping
honest users notice income they forgot to log, not catching cheats.

**Independent Test**: Seed a user with 40,000 recorded income and 90,000 of recorded outflow for
one month, request that month's score, and confirm the score is returned marked incomplete with a
50,000 gap and a resolution prompt. Then acknowledge a drawdown and confirm the period becomes
complete with the score value unchanged.

**Acceptance Scenarios**:

1. **Given** a user records 40,000 of income and 90,000 of combined expenses, savings, investments,
   and deductions for a month, **When** they request that month's score, **Then** the score is
   returned marked incomplete, showing a gap of 50,000 and a prompt to resolve it.
2. **Given** a user whose period is flagged incomplete, **When** they read the prompt, **Then** it
   states the size of the gap and offers to record missing income or confirm a drawdown, and it
   does not assert or imply that the user has been dishonest.
3. **Given** a flagged period, **When** the user confirms they drew on savings held before the
   period, **Then** the period becomes complete, the score value is unchanged, and the user is not
   prompted about it again.
4. **Given** a flagged period, **When** the user instead records the missing 50,000 of income,
   **Then** the period becomes complete and the score is recalculated on the corrected money in.
5. **Given** a user whose outflow exceeds money in by an amount within the tolerance, **When** the
   score is computed, **Then** the period is treated as complete and no prompt is raised.
6. **Given** a user who acknowledged a drawdown for a period, **When** they later add expenses that
   widen the gap beyond the acknowledged amount, **Then** the period is flagged again.
7. **Given** a user records savings and investments exceeding their recorded income, **When** the
   score is computed, **Then** the score is capped at 100 and the period is flagged incomplete.

---

### User Story 4 - Add friends and compare (Priority: P3)

The user searches for another user by display name or handle, sends a friend request, and once it
is accepted the two are connected. The user sees a comparison view listing themselves and their
friends for a chosen period, ranked by stash score, showing each person's score, band, change since
their last period, and how many consecutive complete months they have logged — and nothing else.
Requests can be declined, friendships removed, and users blocked. Seeing a friend sitting well
above them is the prompt to go ask that friend what they did differently — the product starts that
conversation rather than replacing it.

**Why this priority**: Social comparison is the growth and retention mechanic, but it delivers
nothing until there are scores worth comparing. It is also the part with the highest privacy risk,
so it ships after the core is stable.

**Independent Test**: Create two users with known entries, connect them via a request and an
acceptance, and confirm each sees the other's score, band, change, and streak in the comparison
view — and confirm that before the friendship exists neither can see the other's score at all, and
that no amount, entry, or category is ever retrievable.

**Acceptance Scenarios**:

1. **Given** two registered users, **When** user A sends a friend request to user B and B accepts,
   **Then** each appears in the other's friend list.
2. **Given** a pending friend request, **When** the recipient declines it, **Then** the requester
   is not added as a friend and is not told whether the request was declined or merely unanswered.
3. **Given** user A and user B are not friends, **When** A finds B in search or requests B's score
   by any means, **Then** A sees B's display name only, and B's score and band are not returned.
4. **Given** a user with three friends who all have complete scores for the selected month,
   **When** they open the comparison view, **Then** all four people are listed ranked by score,
   highest first, each showing their change since the prior month and their streak.
5. **Given** a friend has no entries for the selected period, **When** the comparison view is
   opened, **Then** that friend is shown as "no score for this period" rather than as zero or
   last place.
6. **Given** a friend's selected period is flagged incomplete, **When** the comparison view is
   opened, **Then** that friend is shown as having an incomplete period, with no score number and
   no rank.
7. **Given** user A is friends with user B, **When** A attempts to retrieve B's entries, amounts,
   income, or category breakdown by any means, **Then** the request is refused.
8. **Given** user A blocks user B, **When** B searches for A or opens any comparison view,
   **Then** A is absent and B cannot send A a new request.
9. **Given** a user removes a friend, **When** either user opens the comparison view, **Then** the
   other no longer appears and neither can see the other's score.

---

### Edge Cases

- **No income recorded**: A user records expenses and savings but never records money coming in.
  The system cannot compute a proportion and MUST return "not enough data" rather than dividing by
  zero or inferring income.
- **Stashed more than earned**: A user moves 60,000 into investments in a month where only 40,000
  came in (drawing on prior balances). The proportion exceeds 100% and MUST be capped at 100, and
  the period MUST be flagged for reconciliation.
- **Genuine long-term drawdown**: A retiree, a person on sabbatical, or someone between jobs has
  little or no income for months while spending from reserves. Every period will flag. The
  acknowledgment MUST be quick, MUST be available for a period before the prompt is dismissed, and
  MUST never reduce the score or the user's standing.
- **Withdrawals from savings**: A user deposits 10,000 into savings and later withdraws 8,000 in
  the same month. The period counts the net 2,000, and a month whose net is negative contributes
  zero rather than a negative amount.
- **Gap exactly at the tolerance boundary**: A period whose gap equals the tolerance must resolve
  deterministically to one side and must not oscillate between complete and incomplete on repeated
  reads.
- **Acknowledged period later corrected**: A user acknowledges a drawdown, then records the missing
  income after all. The period must settle as complete with the corrected score, without a
  lingering acknowledgment that would mask a future gap.
- **Every friend incomplete**: All of a user's friends have unreconciled periods for the selected
  month, leaving no ranked entries. The comparison view must render meaningfully rather than
  appearing broken.
- **Streak interrupted**: A single unreconciled or empty month breaks a run of complete months; the
  streak count must reflect the break rather than spanning it.
- **Partial periods**: A user joins mid-month, or requests a yearly score in February. The score
  is computed on the months that have data, and the response states the coverage.
- **Backdated and retroactive edits**: Entries added or edited long after the fact change already
  published scores and reconciliation states, including scores a friend has already seen.
- **Timezone and period boundaries**: An entry recorded near midnight on the last day of a month
  must land in exactly one period, decided by the user's own timezone, not the server's.
- **Duplicate submissions**: A user's client retries a submission after a timeout; the entry must
  not be recorded twice.
- **Friend request to a non-existent, already-befriended, or blocking user**; a request sent to
  oneself; two users sending each other requests simultaneously.
- **Friend with zero visible history**: A brand new friend appears in the comparison view with no
  score for any period.
- **Friendship revoked mid-view**: A user is looking at a comparison view when the other party
  removes or blocks them; the next request must no longer return that person's score.
- **Large history**: A user with several years and tens of thousands of entries requests a yearly
  summary and a comparison across many friends.
- **Account deletion**: A user deletes their account while appearing in other users' friend lists
  and comparison views.
- **Score gaming**: A user cycles money between savings and back out to inflate their score;
  netting removes the benefit. A user under-reports income; reconciliation flags the period and
  removes it from rankings unless they also under-report expenses, which costs them the expense
  tracking they came for. Fully consistent falsification remains possible and is accepted (see
  Assumptions).

## Requirements *(mandatory)*

### Functional Requirements

**Accounts and identity**

- **FR-001**: System MUST allow a person to register an account, authenticate, and sign out.
- **FR-002**: System MUST allow a user to set a display name, a base currency, and a timezone,
  and MUST use that timezone to decide which period an entry belongs to.
- **FR-003**: System MUST allow a user to permanently delete their account, which removes their
  entries, scores, and friendships, and removes them from every other user's friend list and
  comparison view.

**Recording financial entries**

- **FR-004**: Users MUST be able to record an entry of one of five types: `income`, `expense`,
  `saving`, `investment`, or `deduction`.
- **FR-005**: Every entry MUST carry an amount greater than zero, a currency, a date not in the
  future, a type, and a category; a free-text note is optional.
- **FR-006**: System MUST reject entries with a non-positive amount, a future date, an unknown
  type, or an unknown category, and MUST explain which field failed.
- **FR-007**: Users MUST be able to list, filter (by date range, type, and category), edit, and
  delete their own entries, and MUST NOT be able to read or modify any entry belonging to another
  user.
- **FR-008**: System MUST provide a default set of categories per entry type and MUST allow users
  to create their own categories.
- **FR-009**: Users MUST be able to record a withdrawal from savings or a divestment, and the
  system MUST net these against deposits when computing the amount stashed for a period.
- **FR-010**: System MUST ensure a retried submission of the same entry does not create a
  duplicate.

**Summaries**

- **FR-011**: System MUST produce, for any requested month or year, the total per entry type and
  the total per category, together with the number of entries contributing to each total.
- **FR-012**: System MUST recalculate any affected summary when an entry is added, edited, or
  deleted, including entries backdated into an earlier period.

**Stash score**

- **FR-013**: System MUST compute a stash score for any requested month or year on a 0–100 scale.
- **FR-014**: The score MUST be the proportion of the user's money in for the period that was
  converted into savings and investments, expressed as a number from 0 to 100 — where money in is
  total `income` for the period and the amount stashed is net `saving` plus net `investment`. A
  user who takes in 100 and stashes 10 scores 10; a user who takes in 10 and stashes 5 scores 50.
- **FR-015**: The score MUST NOT be influenced by the absolute size of a user's income, so that
  users at different income levels are measured on the same baseline.
- **FR-016**: System MUST return, alongside every score, the input figures that produced it
  (money in, amount stashed, resulting proportion) so the user can see why the score is what it is.
- **FR-017**: System MUST cap the score at 100 when the amount stashed meets or exceeds money in,
  and MUST floor it at 0 when the net amount stashed is zero or negative.
- **FR-018**: System MUST return an explicit "insufficient data" state, distinct from a score of
  zero, when a period has no recorded income.
- **FR-019**: System MUST assign each score to a named band and MUST use the same band definitions
  for every user.
- **FR-020**: System MUST include, with each monthly score, the change versus the immediately
  preceding month, and with each yearly score, the count of months that contributed data.
- **FR-021**: System MUST make a user's score history for past periods retrievable so trends can
  be shown.
- **FR-022**: System MUST apply identical scoring rules to every user so that any two scores are
  directly comparable.

**Data completeness and score integrity**

- **FR-023**: For every period containing entries, system MUST compare total money in against total
  outflow (expenses plus net savings plus net investments plus deductions) and MUST compute the
  reconciliation gap by which outflow exceeds money in.
- **FR-024**: System MUST flag a period as `unreconciled` when its reconciliation gap exceeds the
  reconciliation tolerance, and MUST treat a period within tolerance as reconciled.
- **FR-025**: For an unreconciled period, system MUST surface a prompt stating the size of the gap
  and offering exactly two resolutions: record the missing income, or confirm that money held
  before the period was drawn down. The prompt MUST NOT assert, imply, or record that the user has
  been dishonest.
- **FR-026**: Users MUST be able to acknowledge a drawdown for a period. Doing so MUST mark the
  period reconciled without altering any recorded amount and without changing the score value.
- **FR-027**: System MUST assign every score one of three completeness states — `complete`,
  `unreconciled`, or `insufficient data` — and MUST return that state with the score.
- **FR-028**: System MUST re-evaluate a period's reconciliation state whenever an entry in that
  period is added, edited, or deleted. An existing acknowledgment MUST continue to apply while the
  gap remains within the acknowledged amount plus tolerance, and the period MUST be flagged again
  once the gap grows beyond it.
- **FR-029**: Completeness MUST affect only a score's eligibility for comparison and the prompts
  shown to its owner. It MUST NOT change the score value, and the owner MUST always be able to see
  their own score regardless of state.

**Friends and comparison**

- **FR-030**: Users MUST be able to search for other users and send a friend request.
- **FR-031**: A friendship MUST require acceptance by the recipient; recipients MUST be able to
  accept or decline, and a declined request MUST NOT reveal the decline to the requester.
- **FR-032**: Users MUST be able to remove a friend and to block another user; a block MUST hide
  each user from the other's search results and comparison views and MUST prevent further requests.
- **FR-033**: System MUST provide a comparison view listing the user and their accepted friends for
  a chosen period, ranked by stash score, showing each person's display name, score, and band.
- **FR-034**: The comparison view MUST also show, for each person, their change in score since the
  preceding period and their current streak of consecutive complete months, so that progress and
  consistency are visible alongside absolute standing.
- **FR-035**: Only scores in the `complete` state MUST be eligible for ranking in a comparison
  view. A person whose selected period is `unreconciled` or `insufficient data` MUST be shown as
  having an incomplete period, with no score number and no rank.
- **FR-036**: System MUST expose a user's score, band, change, and streak **only** to that user and
  to their accepted friends. To every other user — including pending requesters, blocked users, and
  former friends — these MUST NOT be returned by any view, including search results.
- **FR-037**: System MUST NOT expose any user's amounts, entries, categories, income, reconciliation
  gap, or summary figures to any other user through any view, including between accepted friends.
- **FR-038**: A friend with no score for the selected period MUST be shown as having no score,
  not as zero and not ranked last.
- **FR-039**: System MUST reject a friend request sent to oneself, to a non-existent user, to an
  existing friend, or to a user who has blocked the requester.

**Cross-cutting**

- **FR-040**: System MUST require authentication for every operation on personal financial data and
  MUST enforce that a request can only ever reach records the requester is entitled to see.
- **FR-041**: System MUST record all monetary values with an explicit currency and MUST NOT combine
  amounts of different currencies into a single total without stating the conversion applied.
- **FR-042**: System MUST return every list of entries or friends in paginated form with an
  enforced maximum page size.
- **FR-043**: System MUST log score computations, reconciliation state changes, and friendship state
  changes with enough detail to reconstruct why a given score, completeness state, or visibility
  decision was made, without writing amounts into logs in a way that exposes them.

### Key Entities *(include if feature involves data)*

- **User**: A person using the application. Holds display name, credentials, base currency,
  timezone, and account status. Owns all their entries and scores.
- **Financial Entry**: A single recorded money movement. Holds type (income, expense, saving,
  investment, deduction), amount, currency, date, category, optional note, direction (contribution
  or withdrawal, for savings and investments), and the owning user.
- **Category**: A label grouping entries within a type. Either system-provided or user-created;
  user-created categories belong to a single user.
- **Period Summary**: The aggregated totals for one user over one month or one year — totals by
  type, totals by category, contributing entry counts, and the reconciliation gap between money in
  and outflow.
- **Drawdown Acknowledgment**: A user's confirmation that a given period's gap is explained by
  money held before the period. Holds the period, the gap amount at the time of acknowledgment, and
  when it was given.
- **Stash Score**: A score for one user over one period. Holds the 0–100 value, the band, the
  completeness state (`complete`, `unreconciled`, `insufficient data`), the input figures used
  (money in, amount stashed, proportion), the change versus the preceding period, the period, and
  when it was computed.
- **Friendship**: A link between two users with a state — pending, accepted, or blocked — recording
  who initiated it and when it last changed. Determines whether one user's score is visible to the
  other.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new user can register and record their first financial entry in under 2 minutes.
- **SC-002**: A user can record a subsequent entry in under 20 seconds.
- **SC-003**: 95% of users who record at least one month of entries can correctly explain what
  their stash score means and name one thing that would raise it, when asked in usability testing.
- **SC-004**: A monthly or yearly summary, a score, and a comparison view each return results
  within 1 second for a user with 3 years of history and 50 friends.
- **SC-005**: Scores and reconciliation states recomputed after a backdated entry are correct and
  visible within 1 minute of the entry being saved.
- **SC-006**: Zero instances, across security testing, of one user retrieving another user's
  amounts, entries, categories, income, or reconciliation gap — and zero instances of a non-friend
  retrieving a user's score or band.
- **SC-007**: Two users with identical savings proportions but 10× different incomes receive
  identical scores, verified across the full range of the scale.
- **SC-008**: 80% of users shown a reconciliation prompt resolve it — by recording the missing
  income or acknowledging a drawdown — within 7 days.
- **SC-009**: At least 60% of reconciliation prompts are resolved by the user recording income they
  had genuinely forgotten, confirming the check mainly serves data completeness rather than policing.
- **SC-010**: Fewer than 2% of users who receive a reconciliation prompt describe it as accusatory
  or unfair in usability testing or support contacts.
- **SC-011**: Users who engage with the comparison view record entries in 30% more months over
  their first six months than users who do not.
- **SC-012**: Median stash score across users who have used the product for six months is at least
  5 points higher than their own first recorded month.
- **SC-013**: The system supports 10,000 users concurrently viewing scores and comparisons without
  a degradation in the response times stated in SC-004.
- **SC-014**: 90% of new users complete their first week without contacting support to ask how the
  score is calculated.

## Out of Scope

- **Recommendations and savings advice**: Deliberately deferred. Ideas for stashing more are
  expected to pass informally between friends — a user sees a friend's higher score and asks them
  what they did. The product's job is to surface the score that prompts that conversation. No
  generated tips, no personalised analysis, no cohort benchmarking. If this returns later it will
  be specified as its own feature.
- **Income verification**: No payslip upload, bank linking, or third-party income confirmation.
  Reconciliation checks a user's figures against each other, never against an external source.
- **Behavioural anomaly detection**: No modelling of suspicious patterns — income dropping while
  expenses hold steady, scores clustering just above band boundaries, implausibly round figures.
  Deferred until there is a user base large enough for such signals to mean anything.
- **Automatic transaction import**: No bank, card, statement, SMS, or aggregator integration.
  Entries are recorded manually.
- **Investment valuation**: The score counts money put *into* investments, not what those
  investments are now worth. No market data, portfolio pricing, or returns tracking.
- **Multi-currency portfolios**: One base currency per user; no conversion between currencies.
- **In-app messaging**: Friends compare scores in the app and talk elsewhere. No chat, comments,
  or reactions.
- **Budgets and goals**: No target-setting, budget envelopes, or goal tracking in this feature.

## Assumptions

- **Manual entry for v1**: Users type their entries in.
- **Income is mandatory**: A proportion-based score cannot be computed without knowing what came
  in, so `income` is a first-class recorded entry type and a period without it yields no score.
- **Reconciliation tolerance**: A period is flagged when outflow exceeds money in by more than 10%
  of money in, subject to a small absolute floor so that trivial gaps on small amounts do not
  trigger a prompt. Both figures are tunable product settings, not fixed by this spec.
- **Reconciliation never punishes the score**: An unreconciled period still shows its score to its
  owner, unchanged. The only consequence is exclusion from friend rankings until resolved. Wrongly
  flagging an honest user who genuinely spent down savings is treated as a worse outcome than
  letting one user post an unverified score, and all copy is written accordingly.
- **Streak definition**: Consecutive months, ending with the selected period, in which the user has
  a `complete` score. A single unreconciled or empty month resets it.
- **Deductions are informational**: Amounts deducted before the user receives money (tax, statutory
  retirement contributions, loan instalments, insurance) are recorded and reported but are not
  counted as stashed money, because the user did not choose them in the period. They do count as
  outflow for reconciliation. Employer-matched retirement contributions are treated the same way
  in v1.
- **Score bands**: Five bands over the 0–100 scale, identical for all users — 0–19, 20–39, 40–59,
  60–79, 80–100. Band names are a product-copy decision and are not fixed by this spec.
- **Single base currency per user**: Each user picks one currency at signup and records entries in
  it.
- **Net contributions count**: Savings and investment amounts are netted against withdrawals and
  divestments within the period, so money cycled in and out does not inflate the score.
- **Consistent falsification is accepted**: A user who under-reports income *and* correspondingly
  under-reports expenses produces a self-consistent ledger that reconciliation cannot detect. This
  is accepted for v1: the product holds no funds, the stakes are social, and the cheat costs the
  user the expense tracking they came for. Detecting it would require verification, which is out of
  scope.
- **Standard authentication**: Email-and-password with session-based or token-based authentication,
  consistent with the security requirements in the project constitution. No SSO for v1.
- **Backend scope**: This repository provides the backend service. Client applications consume it;
  their visual design and platform choices are specified separately.
- **Regulatory posture**: The product records user-entered figures for personal insight only. It
  does not give regulated financial advice, hold funds, or execute transactions.
- **Data retention**: Entries are retained for the life of the account and deleted on account
  deletion, in line with standard practice for personal finance data.
