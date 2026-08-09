package com.stashup.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.regex.Pattern;

import com.stashup.exception.ApiException;
import com.stashup.exception.ErrorCode;

/**
 * A requested reporting period: either one month ({@code 2026-08}) or one year ({@code 2026}).
 *
 * <p>Both are plain calendar values with no timezone. Period membership is therefore a date
 * comparison, not an instant conversion.
 */
public record PeriodRef(Granularity granularity, LocalDate start, LocalDate endInclusive) {

  private static final Pattern MONTH = Pattern.compile("^(\\d{4})-(\\d{2})$");
  private static final Pattern YEAR = Pattern.compile("^(\\d{4})$");

  public enum Granularity {
    MONTH,
    YEAR
  }

  public static PeriodRef parse(String raw) {
    if (raw == null) {
      throw new ApiException(ErrorCode.INVALID_PERIOD);
    }
    if (MONTH.matcher(raw).matches()) {
      YearMonth month = parseMonth(raw);
      return ofMonth(month);
    }
    if (YEAR.matcher(raw).matches()) {
      int year = Integer.parseInt(raw);
      return new PeriodRef(
          Granularity.YEAR, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }
    throw new ApiException(ErrorCode.INVALID_PERIOD);
  }

  public static PeriodRef ofMonth(YearMonth month) {
    return new PeriodRef(Granularity.MONTH, month.atDay(1), month.atEndOfMonth());
  }

  public static PeriodRef ofMonth(LocalDate anyDayInMonth) {
    return ofMonth(YearMonth.from(anyDayInMonth));
  }

  /** The months this period spans, in ascending order. A month period spans exactly itself. */
  public List<YearMonth> months() {
    YearMonth first = YearMonth.from(start);
    YearMonth last = YearMonth.from(endInclusive);
    return java.util.stream.Stream.iterate(first, m -> !m.isAfter(last), m -> m.plusMonths(1))
        .toList();
  }

  public boolean isMonth() {
    return granularity == Granularity.MONTH;
  }

  /** Canonical wire form: {@code YYYY-MM} or {@code YYYY}. */
  public String label() {
    return isMonth() ? YearMonth.from(start).toString() : String.valueOf(start.getYear());
  }

  /** The month immediately before this one; only meaningful for month periods. */
  public PeriodRef previousMonth() {
    return ofMonth(YearMonth.from(start).minusMonths(1));
  }

  private static YearMonth parseMonth(String raw) {
    try {
      return YearMonth.parse(raw);
    } catch (java.time.format.DateTimeParseException ex) {
      throw new ApiException(ErrorCode.INVALID_PERIOD);
    }
  }
}
