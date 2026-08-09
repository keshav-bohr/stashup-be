package com.stashup.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.stashup.domain.PeriodRef;
import com.stashup.exception.ApiException;
import com.stashup.exception.ErrorCode;

class PeriodRefTest {

  @Test
  @DisplayName("a month period covers exactly that calendar month")
  void monthPeriodBounds() {
    PeriodRef period = PeriodRef.parse("2026-02");

    assertThat(period.granularity()).isEqualTo(PeriodRef.Granularity.MONTH);
    assertThat(period.start()).isEqualTo(LocalDate.of(2026, 2, 1));
    assertThat(period.endInclusive()).isEqualTo(LocalDate.of(2026, 2, 28));
    assertThat(period.label()).isEqualTo("2026-02");
  }

  @Test
  @DisplayName("leap years are handled by the calendar, not by arithmetic")
  void leapYearFebruary() {
    assertThat(PeriodRef.parse("2028-02").endInclusive()).isEqualTo(LocalDate.of(2028, 2, 29));
  }

  @Test
  void yearPeriodBounds() {
    PeriodRef period = PeriodRef.parse("2026");

    assertThat(period.granularity()).isEqualTo(PeriodRef.Granularity.YEAR);
    assertThat(period.start()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(period.endInclusive()).isEqualTo(LocalDate.of(2026, 12, 31));
    assertThat(period.months()).hasSize(12);
    assertThat(period.label()).isEqualTo("2026");
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-13", "2026-00", "26-01", "2026-1", "not-a-period", "", "2026-02-01"})
  void malformedPeriodsAreRejected(String raw) {
    assertThatThrownBy(() -> PeriodRef.parse(raw))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.INVALID_PERIOD);
  }

  @Test
  @DisplayName("the previous month crosses a year boundary correctly")
  void previousMonthCrossesYearBoundary() {
    assertThat(PeriodRef.parse("2026-01").previousMonth().label()).isEqualTo("2025-12");
  }

  @Test
  void monthPeriodSpansOnlyItself() {
    assertThat(PeriodRef.ofMonth(YearMonth.of(2026, 8)).months())
        .containsExactly(YearMonth.of(2026, 8));
  }
}
