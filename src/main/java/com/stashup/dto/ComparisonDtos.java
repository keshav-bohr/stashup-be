package com.stashup.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.stashup.domain.ScoreBand;
import com.stashup.dto.UserDtos.UserSummary;

public final class ComparisonDtos {

  private ComparisonDtos() {}

  /** Why a participant could not be ranked for the selected period. */
  public enum UnrankedReason {
    NO_DATA,
    UNRECONCILED,
    INSUFFICIENT_DATA
  }

  /**
   * The complete set of fields one user may see about another.
   *
   * <p>This record is deliberately a closed list. No amount, no income, no category, no
   * reconciliation gap. Adding a field here is the single most likely way this product leaks
   * financial data, so it should be conspicuous in review — and {@code AmountLeakageIT} asserts
   * the shape from the outside as well.
   */
  public record ComparisonEntry(
      UserSummary user,
      boolean isSelf,
      int rank,
      short score,
      ScoreBand band,
      @Nullable Integer changeFromPreviousPeriod,
      int completeMonthStreak) {}

  public record UnrankedParticipant(UserSummary user, boolean isSelf, UnrankedReason reason) {}

  /**
   * @param streakLookbackMonths declared so a client knows a streak equal to this value may
   *     extend further back, rather than presenting a truncated figure as complete
   */
  public record ComparisonResponse(
      String period,
      int streakLookbackMonths,
      List<ComparisonEntry> ranked,
      List<UnrankedParticipant> unranked) {}
}
