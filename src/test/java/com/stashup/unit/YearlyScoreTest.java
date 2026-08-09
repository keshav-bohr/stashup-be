package com.stashup.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.stashup.service.ScoreCalculator;

/**
 * A yearly score sums the underlying figures and derives the proportion once. It must never be
 * the average of the monthly scores.
 *
 * <p>Averaging weights a month with 1,000 of income equally against a month with 100,000, which
 * produces a number describing nobody's actual behaviour. This is an easy mistake to make and an
 * almost invisible one once made, which is why it has a dedicated test.
 */
class YearlyScoreTest {

  private final ScoreCalculator calculator = new ScoreCalculator();

  private record Month(long moneyIn, long stashed) {}

  @Test
  @DisplayName("yearly score aggregates underlying totals, not monthly percentages")
  void yearlyScoreSumsUnderlyingFigures() {
    // 50% of a small month and 5% of a large one.
    List<Month> year = List.of(new Month(1_000L, 500L), new Month(100_000L, 5_000L));

    long moneyIn = year.stream().mapToLong(Month::moneyIn).sum();
    long stashed = year.stream().mapToLong(Month::stashed).sum();
    short aggregated = calculator.scoreFromBasisPoints(
        calculator.proportionBasisPoints(moneyIn, stashed));

    double naiveAverage = year.stream()
        .mapToInt(m -> calculator.scoreFromBasisPoints(
            calculator.proportionBasisPoints(m.moneyIn(), m.stashed())))
        .average()
        .orElseThrow();

    assertThat(aggregated)
        .as("5500 stashed of 101000 in is ~5, not the ~27 an average would report")
        .isEqualTo((short) 5);
    assertThat(naiveAverage).isCloseTo(27.5, org.assertj.core.data.Offset.offset(0.6));
  }

  @Test
  @DisplayName("a year of identical months equals that month's score")
  void uniformYearMatchesTheMonthlyScore() {
    long moneyIn = 50_000L * 12;
    long stashed = 10_000L * 12;

    assertThat(calculator.scoreFromBasisPoints(
            calculator.proportionBasisPoints(moneyIn, stashed)))
        .isEqualTo(calculator.scoreFromBasisPoints(
            calculator.proportionBasisPoints(50_000L, 10_000L)));
  }

  @Test
  @DisplayName("a year with no income at all has no score")
  void yearWithoutIncomeHasNoScore() {
    assertThat(calculator.proportionBasisPoints(0L, 12_000L)).isNull();
  }
}
