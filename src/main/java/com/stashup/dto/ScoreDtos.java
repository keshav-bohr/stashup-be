package com.stashup.dto;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.stashup.domain.Completeness;
import com.stashup.domain.PeriodRef;
import com.stashup.domain.ScoreBand;
import com.stashup.dto.EntryDtos.MoneyDto;

public final class ScoreDtos {

  private ScoreDtos() {}

  /** The figures that produced the score, so a user can see why it is what it is (FR-016). */
  public record ScoreInputs(
      MoneyDto moneyIn, MoneyDto stashed, @Nullable Integer proportionBasisPoints) {}

  /**
   * A score for one period.
   *
   * <p>{@code score} and {@code band} are null only when {@code completeness} is
   * {@code INSUFFICIENT_DATA}. A score of 0 with {@code COMPLETE} is a different fact — income
   * was recorded and nothing was stashed — and clients must render the two differently.
   *
   * @param capped true when the amount stashed met or exceeded money in, so 100 is a ceiling
   *     rather than a measurement
   */
  public record ScoreResponse(
      String period,
      PeriodRef.Granularity granularity,
      @Nullable Short score,
      @Nullable ScoreBand band,
      Completeness completeness,
      boolean capped,
      ScoreInputs inputs,
      @Nullable Integer changeFromPreviousPeriod,
      @Nullable Integer contributingMonths,
      @Nullable Instant computedAt) {}
}
