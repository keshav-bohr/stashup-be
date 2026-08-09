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
import com.stashup.dto.ReconciliationDtos.ReconciliationResponse;
import com.stashup.dto.ReconciliationDtos.Resolution;
import com.stashup.entity.AppUser;
import com.stashup.entity.Category;
import com.stashup.exception.ApiException;
import com.stashup.exception.ErrorCode;
import com.stashup.repository.AppUserRepository;
import com.stashup.repository.CategoryRepository;
import com.stashup.service.EntryService;
import com.stashup.service.ReconciliationViewService;
import com.stashup.service.ScoreService;
import com.stashup.support.MySqlTestBase;

/** User Story 3 end to end: flag, explain, re-flag, and resolve by recording the missing income. */
class ReconciliationFlowIT extends MySqlTestBase {

  private static final Currency INR = Currency.getInstance("INR");
  private static final PeriodRef MARCH = PeriodRef.parse("2026-03");

  @Autowired private EntryService entryService;
  @Autowired private ScoreService scoreService;
  @Autowired private ReconciliationViewService reconciliation;
  @Autowired private AppUserRepository users;
  @Autowired private CategoryRepository categories;
  @Autowired private Clock clock;

  private UUID userId;

  @BeforeEach
  void createUser() {
    userId = users.save(AppUser.register(
        "recon-" + UUID.randomUUID() + "@example.com",
        "hash", "Recon User", INR, "Asia/Kolkata", clock.instant()))
        .getId();
  }

  @Test
  @DisplayName("a period whose outflow far exceeds income is flagged with the gap and a prompt")
  void unbalancedPeriodIsFlagged() {
    record(EntryType.INCOME, 40_000L, "2026-03-01");
    record(EntryType.EXPENSE, 60_000L, "2026-03-05");
    record(EntryType.SAVING, 20_000L, "2026-03-10");
    record(EntryType.DEDUCTION, 10_000L, "2026-03-12");

    ReconciliationResponse view = reconciliation.view(userId, MARCH);

    assertThat(view.state()).isEqualTo(Completeness.UNRECONCILED);
    assertThat(view.moneyIn().amountMinor()).isEqualTo(40_000L);
    assertThat(view.outflow().amountMinor()).isEqualTo(90_000L);
    assertThat(view.gap().amountMinor()).isEqualTo(50_000L);
    assertThat(view.prompt()).isNotNull();
    assertThat(view.prompt().resolutions())
        .containsExactly(Resolution.RECORD_MISSING_INCOME, Resolution.ACKNOWLEDGE_DRAWDOWN);
  }

  @Test
  @DisplayName("acknowledging a drawdown completes the period without changing the score")
  void acknowledgingDoesNotChangeTheScore() {
    record(EntryType.INCOME, 40_000L, "2026-03-01");
    record(EntryType.SAVING, 60_000L, "2026-03-05");

    short before = scoreService.score(userId, MARCH).score();

    ReconciliationResponse after = reconciliation.acknowledge(userId, MARCH);

    assertThat(after.state()).isEqualTo(Completeness.COMPLETE);
    assertThat(after.prompt()).isNull();
    assertThat(after.acknowledgment()).isNotNull();
    assertThat(after.acknowledgment().acknowledgedGap().amountMinor()).isEqualTo(20_000L);
    assertThat(scoreService.score(userId, MARCH).score())
        .as("completeness gates comparison eligibility, never the number itself (FR-029)")
        .isEqualTo(before);
  }

  @Test
  @DisplayName("widening the gap past what was acknowledged flags the period again (FR-028)")
  void wideningTheGapReflags() {
    record(EntryType.INCOME, 40_000L, "2026-03-01");
    record(EntryType.EXPENSE, 90_000L, "2026-03-05");
    reconciliation.acknowledge(userId, MARCH);
    assertThat(reconciliation.view(userId, MARCH).state()).isEqualTo(Completeness.COMPLETE);

    record(EntryType.EXPENSE, 40_000L, "2026-03-20");

    assertThat(reconciliation.view(userId, MARCH).state())
        .as("the user acknowledged a 50,000 drawdown, not an unlimited one")
        .isEqualTo(Completeness.UNRECONCILED);
  }

  @Test
  @DisplayName("recording the missing income closes the gap and drops the acknowledgment")
  void recordingMissingIncomeResolvesAndClearsAcknowledgment() {
    record(EntryType.INCOME, 40_000L, "2026-03-01");
    record(EntryType.EXPENSE, 90_000L, "2026-03-05");
    reconciliation.acknowledge(userId, MARCH);

    record(EntryType.INCOME, 50_000L, "2026-03-02");

    ReconciliationResponse view = reconciliation.view(userId, MARCH);
    assertThat(view.state()).isEqualTo(Completeness.COMPLETE);
    assertThat(view.gap().amountMinor()).isZero();
    assertThat(view.acknowledgment())
        .as("a stale acknowledgment must not linger and absorb a future gap")
        .isNull();
  }

  @Test
  @DisplayName("a gap inside tolerance never raises a prompt")
  void smallGapStaysWithinTolerance() {
    // 10% of 100,000 is 10,000, so a 5,000 gap is inside tolerance.
    record(EntryType.INCOME, 100_000L, "2026-03-01");
    record(EntryType.EXPENSE, 105_000L, "2026-03-05");

    ReconciliationResponse view = reconciliation.view(userId, MARCH);

    assertThat(view.gap().amountMinor()).isEqualTo(5_000L);
    assertThat(view.tolerance().amountMinor()).isEqualTo(10_000L);
    assertThat(view.state()).isEqualTo(Completeness.COMPLETE);
    assertThat(view.prompt()).isNull();
  }

  @Test
  @DisplayName("stashing beyond income is both capped at 100 and flagged")
  void cappedScoreIsAlsoFlagged() {
    record(EntryType.INCOME, 40_000L, "2026-03-01");
    record(EntryType.INVESTMENT, 60_000L, "2026-03-02");

    assertThat(scoreService.score(userId, MARCH).score()).isEqualTo((short) 100);
    assertThat(scoreService.score(userId, MARCH).capped()).isTrue();
    assertThat(reconciliation.view(userId, MARCH).state())
        .isEqualTo(Completeness.UNRECONCILED);
  }

  @Test
  @DisplayName("a period with no income is insufficient data, which outranks reconciliation")
  void noIncomeTakesPrecedenceOverReconciliation() {
    record(EntryType.EXPENSE, 90_000L, "2026-03-05");

    assertThat(reconciliation.view(userId, MARCH).state())
        .as("with no denominator there is no score for an unexplained gap to protect")
        .isEqualTo(Completeness.INSUFFICIENT_DATA);
  }

  @Test
  @DisplayName("there is nothing to acknowledge on a balanced period")
  void acknowledgingABalancedPeriodIsRejected() {
    record(EntryType.INCOME, 100_000L, "2026-03-01");
    record(EntryType.EXPENSE, 50_000L, "2026-03-05");

    assertThatThrownBy(() -> reconciliation.acknowledge(userId, MARCH))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.NO_GAP_TO_ACKNOWLEDGE);
  }

  @Test
  @DisplayName("withdrawing an acknowledgment re-flags the period")
  void withdrawingAnAcknowledgmentReflags() {
    record(EntryType.INCOME, 40_000L, "2026-03-01");
    record(EntryType.EXPENSE, 90_000L, "2026-03-05");
    reconciliation.acknowledge(userId, MARCH);

    reconciliation.withdraw(userId, MARCH);

    assertThat(reconciliation.view(userId, MARCH).state())
        .isEqualTo(Completeness.UNRECONCILED);
  }

  private EntryResponse record(EntryType type, long minor, String date) {
    List<Category> available = categories.findVisibleToByType(userId, type);
    return entryService.create(userId, new CreateEntryRequest(
        type, Direction.CONTRIBUTION, new MoneyDto(minor, "INR"),
        LocalDate.parse(date), available.get(0).getId(), null));
  }
}
