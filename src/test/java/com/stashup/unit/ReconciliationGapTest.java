package com.stashup.unit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.stashup.period.PeriodTotals;

/**
 * The reconciliation gap: how much a period's outflow exceeds its recorded money in.
 *
 * <p>A non-zero gap is the signal that a period does not account for itself — usually because
 * income was forgotten, occasionally because the user genuinely spent from prior balances.
 */
class ReconciliationGapTest {

  @Test
  @DisplayName("outflow exceeding money in produces the difference as the gap")
  void unbalancedPeriodProducesGap() {
    // 40,000 in; 90,000 out across expenses, savings, and deductions.
    PeriodTotals totals = new PeriodTotals(40_000L, 60_000L, 20_000L, 5_000L, 5_000L, 6);

    assertThat(totals.outflowMinor()).isEqualTo(90_000L);
    assertThat(totals.gapMinor()).isEqualTo(50_000L);
  }

  @Test
  @DisplayName("a balanced period has no gap")
  void balancedPeriodHasNoGap() {
    PeriodTotals totals = new PeriodTotals(100_000L, 70_000L, 20_000L, 10_000L, 0L, 5);

    assertThat(totals.outflowMinor()).isEqualTo(100_000L);
    assertThat(totals.gapMinor()).isZero();
  }

  @Test
  @DisplayName("living below your means is not a gap")
  void underspendingProducesNoGap() {
    PeriodTotals totals = new PeriodTotals(100_000L, 20_000L, 10_000L, 0L, 0L, 3);

    assertThat(totals.gapMinor()).as("gap is floored at zero").isZero();
  }

  @Test
  @DisplayName("stashing more than earned shows up as a gap")
  void stashingBeyondIncomeShowsAsGap() {
    PeriodTotals totals = new PeriodTotals(40_000L, 0L, 60_000L, 0L, 0L, 2);

    assertThat(totals.gapMinor()).isEqualTo(20_000L);
  }

  @Test
  @DisplayName("withdrawals reduce outflow, so drawing on savings to spend balances out")
  void withdrawalFundedSpendingReconciles() {
    // Spent 30,000 while taking 20,000 back out of savings, on 10,000 of income.
    PeriodTotals totals = new PeriodTotals(10_000L, 30_000L, -20_000L, 0L, 0L, 3);

    assertThat(totals.outflowMinor()).isEqualTo(10_000L);
    assertThat(totals.gapMinor())
        .as("the withdrawal explains the spending, so nothing is unaccounted for")
        .isZero();
  }
}
