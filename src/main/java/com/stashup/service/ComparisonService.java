package com.stashup.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stashup.domain.Completeness;
import com.stashup.domain.PeriodRef;
import com.stashup.domain.ScoreBand;
import com.stashup.dto.ComparisonDtos.ComparisonEntry;
import com.stashup.dto.ComparisonDtos.ComparisonResponse;
import com.stashup.dto.ComparisonDtos.UnrankedParticipant;
import com.stashup.dto.ComparisonDtos.UnrankedReason;
import com.stashup.dto.UserDtos.UserSummary;
import com.stashup.entity.AppUser;
import com.stashup.entity.PeriodSummary;
import com.stashup.repository.AppUserRepository;
import com.stashup.repository.PeriodSummaryRepository;

/**
 * Ranks the viewer and their accepted friends by stash score for a period.
 *
 * <p>All participants' summaries are fetched in a <em>single</em> indexed query rather than one
 * lookup per friend. That is the entire reason period totals are materialised, and
 * {@code ComparisonQueryCountTest} guards it.
 *
 * <p>Ranking is by {@code proportion_bp}, not by the displayed score, so two friends who both
 * show 30 still order deterministically instead of swapping places between requests.
 */
@Service
public class ComparisonService {

  private final FriendVisibilityService visibility;
  private final PeriodSummaryRepository summaries;
  private final AppUserRepository users;
  private final ScoreCalculator scoreCalculator;
  private final StreakCalculator streakCalculator;

  public ComparisonService(
      FriendVisibilityService visibility,
      PeriodSummaryRepository summaries,
      AppUserRepository users,
      ScoreCalculator scoreCalculator,
      StreakCalculator streakCalculator) {
    this.visibility = visibility;
    this.summaries = summaries;
    this.users = users;
    this.scoreCalculator = scoreCalculator;
    this.streakCalculator = streakCalculator;
  }

  @Transactional(readOnly = true)
  public ComparisonResponse compare(UUID viewerId, PeriodRef period) {
    Set<UUID> participants = visibility.comparisonParticipants(viewerId);
    YearMonth lastMonth = YearMonth.from(period.endInclusive());

    // One query covers both the period itself and the bounded streak lookback.
    LocalDate windowStart = min(
        period.start(), streakCalculator.lookbackStart(lastMonth));
    List<PeriodSummary> rows =
        summaries.findForUsersInRange(participants, windowStart, period.endInclusive());

    Map<UUID, List<PeriodSummary>> byUser = new HashMap<>();
    for (PeriodSummary row : rows) {
      byUser.computeIfAbsent(row.getUserId(), key -> new ArrayList<>()).add(row);
    }

    Map<UUID, AppUser> profiles = new HashMap<>();
    users.findAllById(participants).forEach(user -> profiles.put(user.getId(), user));

    List<Scored> scored = new ArrayList<>();
    List<UnrankedParticipant> unranked = new ArrayList<>();

    for (UUID participantId : participants) {
      AppUser profile = profiles.get(participantId);
      if (profile == null) {
        continue;
      }
      UserSummary summary = new UserSummary(
          profile.getId(),
          profile.getDisplayName(),
          visibility.statusLabel(viewerId, participantId));
      boolean isSelf = participantId.equals(viewerId);

      List<PeriodSummary> inPeriod = inPeriod(byUser.get(participantId), period);
      @Nullable UnrankedReason reason = reasonFor(inPeriod);
      if (reason != null) {
        unranked.add(new UnrankedParticipant(summary, isSelf, reason));
        continue;
      }
      scored.add(score(summary, isSelf, inPeriod, byUser.get(participantId), lastMonth));
    }

    scored.sort(Comparator.comparingInt(Scored::proportionBp).reversed()
        .thenComparing(entry -> entry.user().displayName()));

    List<ComparisonEntry> ranked = new ArrayList<>(scored.size());
    for (int i = 0; i < scored.size(); i++) {
      Scored entry = scored.get(i);
      ranked.add(new ComparisonEntry(
          entry.user(),
          entry.isSelf(),
          i + 1,
          entry.score(),
          entry.band(),
          entry.change(),
          entry.streak()));
    }

    return new ComparisonResponse(
        period.label(), streakCalculator.lookbackMonths(), ranked, unranked);
  }

  /**
   * A participant is rankable only if every month contributing to the period is complete. For a
   * year that means all twelve; a single unreconciled month makes the year unrankable rather than
   * quietly ranking on partial data.
   */
  private static @Nullable UnrankedReason reasonFor(List<PeriodSummary> inPeriod) {
    if (inPeriod.isEmpty()) {
      return UnrankedReason.NO_DATA;
    }
    if (inPeriod.stream().anyMatch(r -> r.getCompleteness() == Completeness.UNRECONCILED)) {
      return UnrankedReason.UNRECONCILED;
    }
    if (inPeriod.stream().noneMatch(r -> r.getCompleteness() == Completeness.COMPLETE)) {
      return UnrankedReason.INSUFFICIENT_DATA;
    }
    return null;
  }

  private Scored score(
      UserSummary user,
      boolean isSelf,
      List<PeriodSummary> inPeriod,
      @Nullable List<PeriodSummary> allRows,
      YearMonth lastMonth) {

    long moneyIn = inPeriod.stream().mapToLong(PeriodSummary::getMoneyInMinor).sum();
    long stashed = inPeriod.stream().mapToLong(PeriodSummary::getStashedMinor).sum();
    Integer proportionBp = scoreCalculator.proportionBasisPoints(moneyIn, stashed);
    int bp = proportionBp == null ? 0 : proportionBp;
    short value = scoreCalculator.scoreFromBasisPoints(bp);

    return new Scored(
        user,
        isSelf,
        bp,
        value,
        ScoreBand.forScore(value),
        changeFromPrevious(allRows, lastMonth, value),
        streak(allRows, lastMonth));
  }

  /** Null rather than zero when there is nothing to compare against. */
  private static @Nullable Integer changeFromPrevious(
      @Nullable List<PeriodSummary> allRows, YearMonth lastMonth, short current) {

    if (allRows == null) {
      return null;
    }
    YearMonth previous = lastMonth.minusMonths(1);
    return allRows.stream()
        .filter(row -> YearMonth.from(row.getPeriodStart()).equals(previous))
        .map(PeriodSummary::getScore)
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .map(previousScore -> current - previousScore)
        .orElse(null);
  }

  private int streak(@Nullable List<PeriodSummary> allRows, YearMonth lastMonth) {
    if (allRows == null) {
      return 0;
    }
    Map<YearMonth, Completeness> byMonth = new HashMap<>();
    for (PeriodSummary row : allRows) {
      byMonth.put(YearMonth.from(row.getPeriodStart()), row.getCompleteness());
    }
    return streakCalculator.streakEndingAt(lastMonth, byMonth);
  }

  private static List<PeriodSummary> inPeriod(
      @Nullable List<PeriodSummary> rows, PeriodRef period) {
    if (rows == null) {
      return List.of();
    }
    return rows.stream()
        .filter(row -> !row.getPeriodStart().isBefore(period.start())
            && !row.getPeriodStart().isAfter(period.endInclusive()))
        .toList();
  }

  private static LocalDate min(LocalDate left, LocalDate right) {
    return left.isBefore(right) ? left : right;
  }

  /** Intermediate holder so ranking can sort on basis points before assigning positions. */
  private record Scored(
      UserSummary user,
      boolean isSelf,
      int proportionBp,
      short score,
      ScoreBand band,
      @Nullable Integer change,
      int streak) {}
}
