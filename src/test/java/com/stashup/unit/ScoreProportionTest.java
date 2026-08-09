package com.stashup.unit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.stashup.service.ScoreCalculator;

/**
 * The decisive property of the whole product: the score measures a <em>proportion</em>, not an
 * amount.
 *
 * <p>If someone later reimplements scoring on absolute amounts — the most likely regression,
 * because the original feature description said "purely based on the amount" — these tests fail
 * loudly rather than the leaderboard quietly turning into an income ranking.
 */
class ScoreProportionTest {

  private final ScoreCalculator calculator = new ScoreCalculator();

  @Test
  @DisplayName("the lower earner who stashes a greater share scores higher")
  void lowerEarnerStashingMoreProportionallyScoresHigher() {
    // The user's own worked example: earns 100 stashes 10, versus earns 10 stashes 5.
    short bigEarner = score(100, 10);
    short smallEarner = score(10, 5);

    assertThat(bigEarner).isEqualTo((short) 10);
    assertThat(smallEarner).isEqualTo((short) 50);
    assertThat(smallEarner)
        .as("a proportion-based score must not reward the larger absolute amount")
        .isGreaterThan(bigEarner);
  }

  @ParameterizedTest(name = "income {0}, stashed {1} -> score {2}")
  @CsvSource({
    "100000, 30000, 30",
    "100000, 0, 0",
    "100000, 50000, 50",
    "100000, 100000, 100",
    "1, 1, 100",
    "3, 1, 33",
    "7, 1, 14",
  })
  void proportionIsScaledToZeroThroughOneHundred(long moneyIn, long stashed, int expected) {
    assertThat(score(moneyIn, stashed)).isEqualTo((short) expected);
  }

  @Test
  @DisplayName("income size does not influence the score (FR-015)")
  void identicalProportionsAtWildlyDifferentIncomesScoreIdentically() {
    // SC-007: verified across the full range, at a 10x and a 1000x income difference.
    for (int percent = 0; percent <= 100; percent += 5) {
      short modest = score(10_000L, 10_000L * percent / 100);
      short comfortable = score(100_000L, 100_000L * percent / 100);
      short wealthy = score(10_000_000L, 10_000_000L * percent / 100);

      assertThat(modest).isEqualTo(comfortable).isEqualTo(wealthy);
    }
  }

  @Test
  @DisplayName("basis points preserve ordering where the displayed score cannot")
  void nearIdenticalProportionsStillOrderDeterministically() {
    // 30.41% and 30.48% both display as 30, so ranking on the displayed score would order these
    // two friends arbitrarily. Ranking on basis points keeps it deterministic.
    Integer lower = calculator.proportionBasisPoints(100_000L, 30_410L);
    Integer higher = calculator.proportionBasisPoints(100_000L, 30_480L);

    assertThat(calculator.scoreFromBasisPoints(lower))
        .as("both round to the same displayed score")
        .isEqualTo(calculator.scoreFromBasisPoints(higher))
        .isEqualTo((short) 30);
    assertThat(lower).as("but basis points keep them orderable").isLessThan(higher);
  }

  private short score(long moneyIn, long stashed) {
    Integer bp = calculator.proportionBasisPoints(moneyIn, stashed);
    assertThat(bp).isNotNull();
    return calculator.scoreFromBasisPoints(bp);
  }
}
