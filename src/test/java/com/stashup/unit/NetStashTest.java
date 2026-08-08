package com.stashup.unit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.stashup.entry.Direction;
import com.stashup.entry.EntryType;
import com.stashup.period.PeriodTotals;

/**
 * Netting is what stops the simplest way of gaming the score: moving the same money into savings
 * and back out repeatedly.
 */
class NetStashTest {

  @Test
  @DisplayName("a deposit followed by a withdrawal counts only the net")
  void depositThenWithdrawalCountsNet() {
    // 10,000 deposited, 8,000 withdrawn in the same month.
    PeriodTotals totals = new PeriodTotals(100_000L, 0L, 10_000L - 8_000L, 0L, 0L, 2);

    assertThat(totals.stashedMinor()).isEqualTo(2_000L);
  }

  @Test
  @DisplayName("money cycled in and straight back out counts as nothing")
  void cycledMoneyContributesNothing() {
    PeriodTotals totals = new PeriodTotals(100_000L, 0L, 0L, 0L, 0L, 20);

    assertThat(totals.stashedMinor()).isZero();
  }

  @Test
  @DisplayName("a net-negative month contributes zero, never a negative")
  void netNegativeMonthContributesZero() {
    PeriodTotals totals = new PeriodTotals(100_000L, 0L, -15_000L, -5_000L, 0L, 4);

    assertThat(totals.stashedMinor()).isZero();
  }

  @Test
  @DisplayName("savings and investments both count toward the stash")
  void savingsAndInvestmentsBothCount() {
    PeriodTotals totals = new PeriodTotals(100_000L, 0L, 12_000L, 8_000L, 0L, 2);

    assertThat(totals.stashedMinor()).isEqualTo(20_000L);
  }

  @Test
  @DisplayName("deductions are outflow but never stash (they were not chosen in the period)")
  void deductionsAreOutflowButNotStash() {
    PeriodTotals totals = new PeriodTotals(100_000L, 0L, 0L, 0L, 25_000L, 1);

    assertThat(totals.stashedMinor()).isZero();
    assertThat(totals.outflowMinor()).isEqualTo(25_000L);
  }

  @Test
  @DisplayName("only savings and investments support withdrawal (FR-009)")
  void onlyStashableTypesSupportWithdrawal() {
    assertThat(EntryType.SAVING.supportsWithdrawal()).isTrue();
    assertThat(EntryType.INVESTMENT.supportsWithdrawal()).isTrue();
    assertThat(EntryType.INCOME.supportsWithdrawal()).isFalse();
    assertThat(EntryType.EXPENSE.supportsWithdrawal()).isFalse();
    assertThat(EntryType.DEDUCTION.supportsWithdrawal()).isFalse();

    assertThat(Direction.WITHDRAWAL.signum()).isEqualTo(-1L);
    assertThat(Direction.CONTRIBUTION.signum()).isEqualTo(1L);
  }
}
