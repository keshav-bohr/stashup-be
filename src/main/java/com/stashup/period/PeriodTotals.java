package com.stashup.period;

/**
 * The aggregate figures for one user over one month, before any score is derived.
 *
 * <p>Savings and investments arrive already netted against withdrawals, which is what stops money
 * cycled in and out of savings from inflating a score.
 */
public record PeriodTotals(
    long moneyInMinor,
    long expenseMinor,
    long savingNetMinor,
    long investmentNetMinor,
    long deductionMinor,
    int entryCount) {

  public static PeriodTotals empty() {
    return new PeriodTotals(0L, 0L, 0L, 0L, 0L, 0);
  }

  /**
   * What the user actually put aside. Floored at zero: a month where withdrawals exceeded
   * deposits contributes nothing rather than a negative amount (FR-017).
   */
  public long stashedMinor() {
    return Math.max(0L, Math.addExact(savingNetMinor, investmentNetMinor));
  }

  /**
   * Everything that left, including net amounts moved into savings and investments. This is the
   * figure reconciliation compares against money in.
   */
  public long outflowMinor() {
    return expenseMinor + savingNetMinor + investmentNetMinor + deductionMinor;
  }

  /**
   * How much the outflow exceeds recorded money in. A non-zero gap means the period does not
   * account for itself — either income is missing, or the user drew on money held beforehand.
   */
  public long gapMinor() {
    return Math.max(0L, outflowMinor() - moneyInMinor);
  }

  public boolean hasIncome() {
    return moneyInMinor > 0L;
  }
}
