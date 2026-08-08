package com.stashup.entry;

/** The five kinds of money movement a user can record. */
public enum EntryType {
  /** Money arriving. The denominator of the stash score. */
  INCOME,
  /** Money spent. */
  EXPENSE,
  /** Money set aside. Counts toward the stash score, net of withdrawals. */
  SAVING,
  /** Money invested. Counts toward the stash score, net of divestments. */
  INVESTMENT,
  /**
   * Money removed before the user received it — tax, statutory retirement contributions, loan
   * instalments, insurance. Recorded and reported, but deliberately not counted as stashed:
   * the user did not choose it during the period.
   */
  DEDUCTION;

  /** Only savings and investments can be withdrawn from (FR-009). */
  public boolean supportsWithdrawal() {
    return this == SAVING || this == INVESTMENT;
  }

  public boolean isStashable() {
    return this == SAVING || this == INVESTMENT;
  }
}
