package com.stashup.period;

/**
 * Whether a period's figures account for themselves.
 *
 * <p>These three states must stay distinguishable to the client. Collapsing any two of them
 * misreports a user's month back to them:
 *
 * <ul>
 *   <li>{@code COMPLETE} with a score of 0 — income was recorded and nothing was stashed.
 *   <li>{@code INSUFFICIENT_DATA} — entries exist but no income, so no proportion can exist.
 *   <li>No row at all — nothing was recorded.
 * </ul>
 *
 * <p>Completeness gates eligibility for friend comparison only. It never changes the score value
 * (FR-029): the owner always sees their own number.
 */
public enum Completeness {
  COMPLETE,
  UNRECONCILED,
  INSUFFICIENT_DATA;

  /** Only complete periods may be ranked against friends (FR-035). */
  public boolean isRankable() {
    return this == COMPLETE;
  }
}
