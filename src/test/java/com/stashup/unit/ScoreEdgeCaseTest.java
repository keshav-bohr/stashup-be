package com.stashup.unit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.stashup.period.Completeness;
import com.stashup.period.PeriodTotals;
import com.stashup.score.ScoreCalculator;

/**
 * The three "zero-ish" states must stay distinguishable. Collapsing any two of them misreports a
 * user's month back to them.
 */
class ScoreEdgeCaseTest {

  private final ScoreCalculator calculator = new ScoreCalculator();

  @Test
  @DisplayName("no income yields no proportion, not a zero score (FR-018)")
  void noIncomeYieldsNull() {
    assertThat(calculator.proportionBasisPoints(0L, 5_000L)).isNull();
    assertThat(calculator.proportionBasisPoints(0L, 0L)).isNull();
  }

  @Test
  @DisplayName("income with nothing stashed is a real zero, distinct from no data")
  void incomeButNothingStashedScoresZero() {
    Integer bp = calculator.proportionBasisPoints(100_000L, 0L);

    assertThat(bp).isNotNull().isZero();
    assertThat(calculator.scoreFromBasisPoints(bp)).isZero();
  }

  @Test
  @DisplayName("stashing more than earned caps at 100 rather than overflowing (FR-017)")
  void stashingMoreThanEarnedCapsAtOneHundred() {
    Integer bp = calculator.proportionBasisPoints(40_000L, 60_000L);

    assertThat(bp).isEqualTo(ScoreCalculator.FULL_SCALE_BP);
    assertThat(calculator.scoreFromBasisPoints(bp)).isEqualTo((short) 100);
    assertThat(calculator.isCapped(40_000L, 60_000L)).isTrue();
    assertThat(calculator.isCapped(40_000L, 10_000L)).isFalse();
  }

  @Test
  @DisplayName("a negative net stash floors at zero, never a negative score")
  void negativeNetStashFloorsAtZero() {
    PeriodTotals totals = new PeriodTotals(100_000L, 0L, -8_000L, -2_000L, 0L, 3);

    assertThat(totals.stashedMinor()).isZero();
    Integer bp = calculator.proportionBasisPoints(totals.moneyInMinor(), totals.stashedMinor());
    assertThat(bp).isNotNull().isZero();
  }

  @Test
  @DisplayName("insufficient data is reachable only when there is no income")
  void completenessDependsOnIncomePresence() {
    assertThat(new PeriodTotals(0L, 5_000L, 0L, 0L, 0L, 1).hasIncome()).isFalse();
    assertThat(new PeriodTotals(1L, 0L, 0L, 0L, 0L, 1).hasIncome()).isTrue();

    // The enum keeps the three states separable for the client.
    assertThat(Completeness.COMPLETE.isRankable()).isTrue();
    assertThat(Completeness.UNRECONCILED.isRankable()).isFalse();
    assertThat(Completeness.INSUFFICIENT_DATA.isRankable()).isFalse();
  }
}
