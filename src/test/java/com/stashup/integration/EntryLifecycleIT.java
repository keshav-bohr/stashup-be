package com.stashup.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.stashup.domain.Completeness;
import com.stashup.domain.Direction;
import com.stashup.domain.EntryType;
import com.stashup.domain.PeriodRef;
import com.stashup.dto.EntryDtos.CreateEntryRequest;
import com.stashup.dto.EntryDtos.EntryResponse;
import com.stashup.dto.EntryDtos.MoneyDto;
import com.stashup.dto.EntryDtos.UpdateEntryRequest;
import com.stashup.dto.ScoreDtos.ScoreResponse;
import com.stashup.entity.AppUser;
import com.stashup.entity.Category;
import com.stashup.exception.ApiException;
import com.stashup.repository.AppUserRepository;
import com.stashup.repository.CategoryRepository;
import com.stashup.service.EntryService;
import com.stashup.service.PeriodSummaryService;
import com.stashup.service.ScoreService;
import com.stashup.support.MySqlTestBase;

/**
 * End-to-end over real MySQL: migrations, JPA mappings, netting, in-transaction recomputation,
 * and cross-user isolation.
 */
class EntryLifecycleIT extends MySqlTestBase {

  private static final Currency INR = Currency.getInstance("INR");

  @Autowired private EntryService entryService;
  @Autowired private ScoreService scoreService;
  @Autowired private PeriodSummaryService summaryService;
  @Autowired private AppUserRepository users;
  @Autowired private CategoryRepository categories;
  @Autowired private Clock clock;

  private UUID userId;

  @BeforeEach
  void createUser() {
    userId = newUser().getId();
  }

  @Test
  @DisplayName("recording entries produces the expected summary totals")
  void recordingEntriesBuildsSummary() {
    record(EntryType.INCOME, 10_000_000L, "2026-03-01");
    record(EntryType.EXPENSE, 1_200_00L, "2026-03-05");

    var summary = summaryService.summarise(userId, PeriodRef.parse("2026-03"));

    assertThat(summary.totalsByType().get(EntryType.INCOME).total().amountMinor())
        .isEqualTo(10_000_000L);
    assertThat(summary.totalsByType().get(EntryType.EXPENSE).total().amountMinor())
        .isEqualTo(120_000L);
    assertThat(summary.entryCount()).isEqualTo(2);
    assertThat(summary.currency()).isEqualTo("INR");
  }

  @Test
  @DisplayName("the score is the proportion of money in that was stashed")
  void scoreIsProportional() {
    record(EntryType.INCOME, 100_000L, "2026-03-01");
    record(EntryType.SAVING, 20_000L, "2026-03-10");
    record(EntryType.INVESTMENT, 10_000L, "2026-03-15");

    ScoreResponse score = scoreService.score(userId, PeriodRef.parse("2026-03"));

    assertThat(score.score()).isEqualTo((short) 30);
    assertThat(score.completeness()).isEqualTo(Completeness.COMPLETE);
    assertThat(score.inputs().moneyIn().amountMinor()).isEqualTo(100_000L);
    assertThat(score.inputs().stashed().amountMinor()).isEqualTo(30_000L);
    assertThat(score.inputs().proportionBasisPoints()).isEqualTo(3_000);
    assertThat(score.capped()).isFalse();
  }

  @Test
  @DisplayName("the smaller earner who stashes a greater share outscores the larger earner")
  void proportionalFairnessHoldsAcrossUsers() {
    record(EntryType.INCOME, 100L, "2026-03-01");
    record(EntryType.SAVING, 10L, "2026-03-02");

    UUID smallEarner = newUser().getId();
    record(smallEarner, EntryType.INCOME, 10L, "2026-03-01");
    record(smallEarner, EntryType.SAVING, 5L, "2026-03-02");

    short big = scoreService.score(userId, PeriodRef.parse("2026-03")).score();
    short small = scoreService.score(smallEarner, PeriodRef.parse("2026-03")).score();

    assertThat(big).isEqualTo((short) 10);
    assertThat(small).isEqualTo((short) 50);
  }

  @Test
  @DisplayName("withdrawals net against deposits within the period")
  void withdrawalsNetAgainstDeposits() {
    record(EntryType.INCOME, 100_000L, "2026-04-01");
    record(EntryType.SAVING, Direction.CONTRIBUTION, 10_000L, "2026-04-05");
    record(EntryType.SAVING, Direction.WITHDRAWAL, 8_000L, "2026-04-20");

    ScoreResponse score = scoreService.score(userId, PeriodRef.parse("2026-04"));

    assertThat(score.inputs().stashed().amountMinor()).isEqualTo(2_000L);
    assertThat(score.score()).isEqualTo((short) 2);
  }

  @Test
  @DisplayName("entries but no income is insufficient data, not a zero score")
  void noIncomeYieldsInsufficientData() {
    record(EntryType.EXPENSE, 5_000L, "2026-05-01");

    ScoreResponse score = scoreService.score(userId, PeriodRef.parse("2026-05"));

    assertThat(score.completeness()).isEqualTo(Completeness.INSUFFICIENT_DATA);
    assertThat(score.score()).isNull();
    assertThat(score.band()).isNull();
  }

  @Test
  @DisplayName("stashing beyond income caps the score at 100")
  void stashingBeyondIncomeCaps() {
    record(EntryType.INCOME, 40_000L, "2026-06-01");
    record(EntryType.INVESTMENT, 60_000L, "2026-06-02");

    ScoreResponse score = scoreService.score(userId, PeriodRef.parse("2026-06"));

    assertThat(score.score()).isEqualTo((short) 100);
    assertThat(score.capped()).isTrue();
  }

  @Test
  @DisplayName("a yearly score aggregates totals rather than averaging monthly scores")
  void yearlyScoreAggregatesTotals() {
    record(EntryType.INCOME, 1_000L, "2026-01-05");
    record(EntryType.SAVING, 500L, "2026-01-06");
    record(EntryType.INCOME, 100_000L, "2026-02-05");
    record(EntryType.SAVING, 5_000L, "2026-02-06");

    ScoreResponse yearly = scoreService.score(userId, PeriodRef.parse("2026"));

    assertThat(yearly.score()).as("5500 of 101000, not the average of 50 and 5").isEqualTo((short) 5);
    assertThat(yearly.contributingMonths()).isEqualTo(2);
  }

  @Test
  @DisplayName("a backdated entry recalculates the closed month immediately")
  void backdatedEntryRecalculatesClosedMonth() {
    record(EntryType.INCOME, 100_000L, "2026-01-10");
    assertThat(scoreService.score(userId, PeriodRef.parse("2026-01")).score()).isZero();

    record(EntryType.SAVING, 25_000L, "2026-01-15");

    assertThat(scoreService.score(userId, PeriodRef.parse("2026-01")).score())
        .as("recomputation happens in the write transaction, not on a delay")
        .isEqualTo((short) 25);
  }

  @Test
  @DisplayName("moving an entry across a month boundary recalculates both months")
  void movingAnEntryRecalculatesBothMonths() {
    record(EntryType.INCOME, 100_000L, "2026-07-01");
    record(EntryType.INCOME, 100_000L, "2026-08-01");
    EntryResponse saving = record(EntryType.SAVING, 40_000L, "2026-07-15");

    assertThat(scoreService.score(userId, PeriodRef.parse("2026-07")).score())
        .isEqualTo((short) 40);

    entryService.update(
        userId,
        saving.id(),
        new UpdateEntryRequest(null, LocalDate.parse("2026-08-05"), null, null, null));

    assertThat(scoreService.score(userId, PeriodRef.parse("2026-07")).score())
        .as("the source month must stop counting an entry it no longer holds")
        .isZero();
    assertThat(scoreService.score(userId, PeriodRef.parse("2026-08")).score())
        .isEqualTo((short) 40);
  }

  @Test
  @DisplayName("deleting the last entry in a month removes the summary entirely")
  void deletingLastEntryRemovesSummary() {
    EntryResponse entry = record(EntryType.INCOME, 100_000L, "2025-11-01");

    entryService.delete(userId, entry.id());

    assertThatThrownBy(() -> scoreService.score(userId, PeriodRef.parse("2025-11")))
        .as("no row at all is distinct from a zero score")
        .isInstanceOf(ApiException.class);
  }

  @Test
  @DisplayName("one user cannot read another user's entry")
  void entriesAreScopedToTheirOwner() {
    EntryResponse mine = record(EntryType.INCOME, 100_000L, "2026-03-01");
    UUID stranger = newUser().getId();

    assertThatThrownBy(() -> entryService.get(stranger, mine.id()))
        .as("returned as not-found, never as forbidden, so IDs cannot be probed")
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code().status().value())
        .isEqualTo(404);
  }

  private AppUser newUser() {
    return users.save(AppUser.register(
        "user-" + UUID.randomUUID() + "@example.com",
        "hash",
        "Test User",
        INR,
        "Asia/Kolkata",
        clock.instant()));
  }

  private EntryResponse record(EntryType type, long minor, String date) {
    return record(userId, type, Direction.CONTRIBUTION, minor, date);
  }

  private EntryResponse record(EntryType type, Direction direction, long minor, String date) {
    return record(userId, type, direction, minor, date);
  }

  private EntryResponse record(UUID owner, EntryType type, long minor, String date) {
    return record(owner, type, Direction.CONTRIBUTION, minor, date);
  }

  private EntryResponse record(
      UUID owner, EntryType type, Direction direction, long minor, String date) {
    List<Category> available = categories.findVisibleToByType(owner, type);
    assertThat(available).as("system categories should be seeded for %s", type).isNotEmpty();

    return entryService.create(owner, new CreateEntryRequest(
        type,
        direction,
        new MoneyDto(minor, "INR"),
        LocalDate.parse(date),
        available.get(0).getId(),
        null));
  }
}
