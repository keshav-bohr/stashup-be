package com.stashup.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.stashup.config.ApplicationProperties;
import com.stashup.domain.Completeness;

/**
 * Consecutive complete months ending at a given period.
 *
 * <p>Computed on read from {@code period_summary} rather than stored. A materialised streak column
 * breaks under backdated edits, which the spec explicitly supports: correcting a month two years
 * ago would require rewriting the entire forward chain.
 *
 * <p>The lookback is bounded, because an unbounded query is forbidden outright. The cap is
 * <em>declared</em> in the API response rather than silently truncating, so a client never
 * presents a capped streak as if it were final.
 */
@Component
public class StreakCalculator {

  private final int lookbackMonths;

  public StreakCalculator(ApplicationProperties properties) {
    this.lookbackMonths = properties.comparison().streakLookbackMonths();
  }

  public int lookbackMonths() {
    return lookbackMonths;
  }

  /** The earliest month a streak calculation will consider. */
  public LocalDate lookbackStart(YearMonth endingAt) {
    return endingAt.minusMonths(lookbackMonths - 1L).atDay(1);
  }

  /**
   * Walks backwards from {@code endingAt}, counting months whose state is {@code COMPLETE}. A
   * month that is unreconciled, insufficient, or entirely absent resets the count.
   *
   * @param completenessByMonth state per month for one user; missing keys are treated as no data
   */
  public int streakEndingAt(
      YearMonth endingAt, Map<YearMonth, Completeness> completenessByMonth) {

    int streak = 0;
    YearMonth cursor = endingAt;
    for (int i = 0; i < lookbackMonths; i++) {
      Completeness state = completenessByMonth.get(cursor);
      if (state == null || state != Completeness.COMPLETE) {
        break;
      }
      streak++;
      cursor = cursor.minusMonths(1);
    }
    return streak;
  }
}
