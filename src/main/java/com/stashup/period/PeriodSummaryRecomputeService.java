package com.stashup.period;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Currency;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stashup.entry.EntryType;
import com.stashup.entry.FinancialEntryRepository;
import com.stashup.score.ScoreCalculator;

/**
 * Recomputes one user's materialised monthly summary.
 *
 * <p>This runs <em>inside</em> the transaction of the entry mutation that invalidated it, not on
 * a queue. The unit of work is a single indexed {@code GROUP BY} over one user's one month —
 * bounded by that user's monthly entry count, not by table size — so it is orders of magnitude
 * inside the write budget. Doing it in-transaction also makes the summary consistent with the
 * entries by construction rather than eventually, so a user never sees a stale score immediately
 * after saving. The trade-off is recorded in plan.md under Complexity Tracking.
 */
@Service
public class PeriodSummaryRecomputeService {

  private final FinancialEntryRepository entries;
  private final PeriodSummaryRepository summaries;
  private final ScoreCalculator scoreCalculator;
  private final Clock clock;

  public PeriodSummaryRecomputeService(
      FinancialEntryRepository entries,
      PeriodSummaryRepository summaries,
      ScoreCalculator scoreCalculator,
      Clock clock) {
    this.entries = entries;
    this.summaries = summaries;
    this.scoreCalculator = scoreCalculator;
    this.clock = clock;
  }

  /**
   * Recomputes the given month. Safe to call for a month that has no entries: the row is removed
   * so that "no data" stays distinguishable from a zero score.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void recompute(UUID userId, YearMonth month, Currency currency) {
    PeriodRef period = PeriodRef.ofMonth(month);
    PeriodTotals totals = aggregate(userId, period.start(), period.endInclusive());

    @Nullable PeriodSummary existing =
        summaries.findByUserIdAndPeriodStart(userId, period.start()).orElse(null);

    if (totals.entryCount() == 0) {
      if (existing != null) {
        summaries.delete(existing);
      }
      return;
    }

    PeriodSummary summary =
        existing != null ? existing : PeriodSummary.empty(userId, period.start(), currency);

    Instant now = clock.instant();
    summary.applyTotals(totals, now);
    applyDerivedScore(summary, totals);
    summary.applyCompleteness(determineCompleteness(totals));
    summaries.save(summary);
  }

  /** Recomputes both months when an edit moves an entry across a month boundary. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void recomputeBoth(
      UUID userId, YearMonth first, @Nullable YearMonth second, Currency currency) {
    recompute(userId, first, currency);
    if (second != null && !second.equals(first)) {
      recompute(userId, second, currency);
    }
  }

  private void applyDerivedScore(PeriodSummary summary, PeriodTotals totals) {
    Integer proportionBp =
        scoreCalculator.proportionBasisPoints(totals.moneyInMinor(), totals.stashedMinor());
    if (proportionBp == null) {
      summary.applyScore(null, null, null);
      return;
    }
    short score = scoreCalculator.scoreFromBasisPoints(proportionBp);
    summary.applyScore(proportionBp, score, ScoreBand.forScore(score));
  }

  /**
   * A period with no recorded income yields no proportion, which is reported as
   * {@code INSUFFICIENT_DATA} rather than a score of zero (FR-018).
   *
   * <p>The {@code UNRECONCILED} branch is added by the reconciliation feature; until then a
   * period with income is complete. Flagging gaps before an acknowledgment mechanism exists
   * would leave users with no way to resolve the flag.
   */
  private static Completeness determineCompleteness(PeriodTotals totals) {
    return totals.hasIncome() ? Completeness.COMPLETE : Completeness.INSUFFICIENT_DATA;
  }

  /** Folds the grouped rollup rows into a {@link PeriodTotals}. */
  private PeriodTotals aggregate(UUID userId, LocalDate start, LocalDate end) {
    List<Object[]> rows = entries.aggregateByType(userId, start, end);
    Map<EntryType, Long> netByType = new EnumMap<>(EntryType.class);
    int entryCount = 0;

    for (Object[] row : rows) {
      EntryType type = (EntryType) row[0];
      long net = ((Number) row[1]).longValue();
      long count = ((Number) row[2]).longValue();
      netByType.put(type, net);
      entryCount += (int) count;
    }

    return new PeriodTotals(
        netByType.getOrDefault(EntryType.INCOME, 0L),
        netByType.getOrDefault(EntryType.EXPENSE, 0L),
        netByType.getOrDefault(EntryType.SAVING, 0L),
        netByType.getOrDefault(EntryType.INVESTMENT, 0L),
        netByType.getOrDefault(EntryType.DEDUCTION, 0L),
        entryCount);
  }
}
