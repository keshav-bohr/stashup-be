package com.stashup.service;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stashup.domain.Completeness;
import com.stashup.domain.Money;
import com.stashup.domain.PeriodRef;
import com.stashup.domain.ScoreBand;
import com.stashup.dto.EntryDtos.MoneyDto;
import com.stashup.dto.ScoreDtos.ScoreInputs;
import com.stashup.dto.ScoreDtos.ScoreResponse;
import com.stashup.entity.PeriodSummary;
import com.stashup.exception.ApiException;
import com.stashup.repository.AppUserRepository;
import com.stashup.repository.PeriodSummaryRepository;

/**
 * Serves stash scores for months and years.
 *
 * <p>The yearly score is <em>not</em> the average of the monthly scores. It is total stashed
 * divided by total money in across the year. Averaging twelve percentages weights a month with
 * 1,000 of income equally against a month with 100,000, which produces a number that describes
 * nobody's actual behaviour.
 */
@Service
public class ScoreService {

  private final PeriodSummaryRepository summaries;
  private final AppUserRepository users;
  private final ScoreCalculator calculator;

  public ScoreService(
      PeriodSummaryRepository summaries, AppUserRepository users, ScoreCalculator calculator) {
    this.summaries = summaries;
    this.users = users;
    this.calculator = calculator;
  }

  @Transactional(readOnly = true)
  public ScoreResponse score(UUID userId, PeriodRef period) {
    Currency currency = users
        .findById(userId)
        .orElseThrow(ApiException::notFound)
        .getBaseCurrency();

    List<PeriodSummary> rows = summaries.findByUserIdAndPeriodStartBetweenOrderByPeriodStartAsc(
        userId, period.start(), period.endInclusive());
    if (rows.isEmpty()) {
      throw ApiException.notFound();
    }
    return period.isMonth()
        ? monthlyScore(userId, period, rows.get(0), currency)
        : yearlyScore(period, rows, currency);
  }

  /** Score history across a range of months, oldest first. */
  @Transactional(readOnly = true)
  public List<ScoreResponse> history(UUID userId, YearMonth from, YearMonth to) {
    Currency currency = users
        .findById(userId)
        .orElseThrow(ApiException::notFound)
        .getBaseCurrency();

    List<PeriodSummary> rows = summaries.findByUserIdAndPeriodStartBetweenOrderByPeriodStartAsc(
        userId, from.atDay(1), to.atEndOfMonth());

    return rows.stream()
        .map(row -> toResponse(
            PeriodRef.ofMonth(row.getPeriodStart()),
            row,
            currency,
            changeVersusPrevious(userId, row)))
        .toList();
  }

  private ScoreResponse monthlyScore(
      UUID userId, PeriodRef period, PeriodSummary row, Currency currency) {
    return toResponse(period, row, currency, changeVersusPrevious(userId, row));
  }

  /**
   * Sums the underlying money-in and stashed figures, then derives the proportion once from the
   * totals.
   */
  private ScoreResponse yearlyScore(
      PeriodRef period, List<PeriodSummary> rows, Currency currency) {

    long moneyIn = rows.stream().mapToLong(PeriodSummary::getMoneyInMinor).sum();
    long stashed = rows.stream().mapToLong(PeriodSummary::getStashedMinor).sum();

    @Nullable Integer proportionBp = calculator.proportionBasisPoints(moneyIn, stashed);
    @Nullable Short score = proportionBp == null ? null
        : calculator.scoreFromBasisPoints(proportionBp);
    @Nullable ScoreBand band = score == null ? null : ScoreBand.forScore(score);

    Completeness completeness = rows.stream().allMatch(r -> r.getCompleteness().isRankable())
        ? Completeness.COMPLETE
        : worstOf(rows);

    @Nullable Instant computedAt = rows.stream()
        .map(PeriodSummary::getComputedAt)
        .max(Instant::compareTo)
        .orElse(null);

    return new ScoreResponse(
        period.label(),
        period.granularity(),
        score,
        band,
        completeness,
        calculator.isCapped(moneyIn, stashed),
        new ScoreInputs(
            MoneyDto.from(Money.of(moneyIn, currency)),
            MoneyDto.from(Money.of(stashed, currency)),
            proportionBp),
        null,
        rows.size(),
        computedAt);
  }

  private ScoreResponse toResponse(
      PeriodRef period,
      PeriodSummary row,
      Currency currency,
      @Nullable Integer changeFromPrevious) {

    return new ScoreResponse(
        period.label(),
        period.granularity(),
        row.getScore(),
        row.getBand(),
        row.getCompleteness(),
        calculator.isCapped(row.getMoneyInMinor(), row.getStashedMinor()),
        new ScoreInputs(
            MoneyDto.from(Money.of(row.getMoneyInMinor(), currency)),
            MoneyDto.from(Money.of(row.getStashedMinor(), currency)),
            row.getProportionBp()),
        changeFromPrevious,
        null,
        row.getComputedAt());
  }

  /**
   * Null rather than zero when the preceding month has no score: "unchanged" and "nothing to
   * compare against" are different statements.
   */
  private @Nullable Integer changeVersusPrevious(UUID userId, PeriodSummary row) {
    @Nullable Short current = row.getScore();
    if (current == null) {
      return null;
    }
    Optional<PeriodSummary> previous = summaries.findByUserIdAndPeriodStart(
        userId, row.getPeriodStart().minusMonths(1));
    return previous
        .map(PeriodSummary::getScore)
        .map(previousScore -> current - previousScore)
        .orElse(null);
  }

  private static Completeness worstOf(List<PeriodSummary> rows) {
    boolean anyUnreconciled =
        rows.stream().anyMatch(r -> r.getCompleteness() == Completeness.UNRECONCILED);
    return anyUnreconciled ? Completeness.UNRECONCILED : Completeness.INSUFFICIENT_DATA;
  }
}
