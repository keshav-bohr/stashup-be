package com.stashup.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.stashup.config.ApplicationProperties;
import com.stashup.domain.Completeness;
import com.stashup.service.StreakCalculator;

class StreakCalculatorTest {

  private static final YearMonth MARCH = YearMonth.of(2026, 3);

  private final StreakCalculator calculator = new StreakCalculator(properties(24));

  @Test
  @DisplayName("consecutive complete months count")
  void consecutiveCompleteMonthsCount() {
    assertThat(calculator.streakEndingAt(MARCH, complete(MARCH, 3))).isEqualTo(3);
  }

  @Test
  @DisplayName("an unreconciled month breaks the run rather than being skipped over")
  void unreconciledMonthBreaksTheStreak() {
    Map<YearMonth, Completeness> months = complete(MARCH, 3);
    months.put(YearMonth.of(2026, 2), Completeness.UNRECONCILED);

    assertThat(calculator.streakEndingAt(MARCH, months))
        .as("a streak spanning a broken month would be a lie")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("an entirely missing month breaks the run")
  void missingMonthBreaksTheStreak() {
    Map<YearMonth, Completeness> months = complete(MARCH, 3);
    months.remove(YearMonth.of(2026, 2));

    assertThat(calculator.streakEndingAt(MARCH, months)).isEqualTo(1);
  }

  @Test
  @DisplayName("an incomplete current month means no streak at all")
  void incompleteCurrentMonthYieldsZero() {
    Map<YearMonth, Completeness> months = complete(MARCH, 3);
    months.put(MARCH, Completeness.INSUFFICIENT_DATA);

    assertThat(calculator.streakEndingAt(MARCH, months)).isZero();
    assertThat(calculator.streakEndingAt(MARCH, Map.of())).isZero();
  }

  @Test
  @DisplayName("the streak is capped at the lookback window, and the cap is declared")
  void streakIsCappedAtTheLookback() {
    StreakCalculator small = new StreakCalculator(properties(6));

    assertThat(small.streakEndingAt(MARCH, complete(MARCH, 24)))
        .as("bounded, because an unbounded lookback query is forbidden outright")
        .isEqualTo(6);
    assertThat(small.lookbackMonths()).isEqualTo(6);
  }

  @Test
  @DisplayName("the lookback window starts inclusive of the earliest considered month")
  void lookbackStartIsInclusive() {
    assertThat(calculator.lookbackStart(MARCH)).isEqualTo(LocalDate.of(2024, 4, 1));
    assertThat(new StreakCalculator(properties(1)).lookbackStart(MARCH))
        .isEqualTo(LocalDate.of(2026, 3, 1));
  }

  private static Map<YearMonth, Completeness> complete(YearMonth endingAt, int count) {
    Map<YearMonth, Completeness> months = new HashMap<>();
    for (int i = 0; i < count; i++) {
      months.put(endingAt.minusMonths(i), Completeness.COMPLETE);
    }
    return months;
  }

  private static ApplicationProperties properties(int lookbackMonths) {
    return new ApplicationProperties(
        new ApplicationProperties.Security(
            "0123456789012345678901234567890123456789",
            Duration.ofMinutes(15), Duration.ofDays(30), 5, Duration.ofMinutes(15)),
        new ApplicationProperties.Pagination(100),
        new ApplicationProperties.Idempotency(Duration.ofHours(24)),
        new ApplicationProperties.Reconciliation(10, 10_000L),
        new ApplicationProperties.Comparison(lookbackMonths),
        new ApplicationProperties.RateLimit(60, 60, 10, 10));
  }
}
