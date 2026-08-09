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

import com.stashup.domain.Direction;
import com.stashup.domain.EntryType;
import com.stashup.domain.FriendshipStatus;
import com.stashup.domain.PeriodRef;
import com.stashup.dto.ComparisonDtos.ComparisonResponse;
import com.stashup.dto.ComparisonDtos.UnrankedReason;
import com.stashup.dto.EntryDtos.CreateEntryRequest;
import com.stashup.dto.EntryDtos.MoneyDto;
import com.stashup.entity.AppUser;
import com.stashup.entity.Category;
import com.stashup.entity.Friendship;
import com.stashup.exception.ApiException;
import com.stashup.exception.ErrorCode;
import com.stashup.repository.AppUserRepository;
import com.stashup.repository.CategoryRepository;
import com.stashup.repository.FriendshipRepository;
import com.stashup.service.ComparisonService;
import com.stashup.service.EntryService;
import com.stashup.service.FriendshipService;
import com.stashup.service.ReconciliationViewService;
import com.stashup.support.MySqlTestBase;

/** The friendship state machine, ranking rules, and streaks. */
class FriendshipLifecycleIT extends MySqlTestBase {

  private static final Currency INR = Currency.getInstance("INR");
  private static final PeriodRef MARCH = PeriodRef.parse("2026-03");

  @Autowired private FriendshipService friendshipService;
  @Autowired private FriendshipRepository friendships;
  @Autowired private ComparisonService comparisonService;
  @Autowired private ReconciliationViewService reconciliation;
  @Autowired private EntryService entryService;
  @Autowired private AppUserRepository users;
  @Autowired private CategoryRepository categories;
  @Autowired private Clock clock;

  private UUID alice;
  private UUID bob;

  @BeforeEach
  void seed() {
    alice = newUser("Alice");
    bob = newUser("Bob");
  }

  @Test
  @DisplayName("request then accept connects both users")
  void requestThenAccept() {
    friendshipService.request(alice, bob);
    Friendship pending = friendships.findBetween(alice, bob).orElseThrow();
    assertThat(pending.getStatus()).isEqualTo(FriendshipStatus.PENDING);
    assertThat(friendshipService.pendingRequests(bob, true)).hasSize(1);
    assertThat(friendshipService.pendingRequests(alice, false)).hasSize(1);

    friendshipService.accept(bob, pending.getId());

    assertThat(friendships.findAccepted(alice)).hasSize(1);
    assertThat(friendships.findAccepted(bob)).hasSize(1);
  }

  @Test
  @DisplayName("declining leaves no trace the requester can observe")
  void declineIsInvisibleToTheRequester() {
    friendshipService.request(alice, bob);
    Friendship pending = friendships.findBetween(alice, bob).orElseThrow();

    friendshipService.decline(bob, pending.getId());

    assertThat(friendships.findBetween(alice, bob)).isEmpty();
    assertThat(friendshipService.pendingRequests(alice, false))
        .as("indistinguishable from never having been answered")
        .isEmpty();
  }

  @Test
  @DisplayName("requesting someone who already requested you is an acceptance")
  void mutualRequestBecomesAcceptance() {
    friendshipService.request(alice, bob);
    friendshipService.request(bob, alice);

    Friendship friendship = friendships.findBetween(alice, bob).orElseThrow();
    assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
    assertThat(friendships.findAllInvolving(alice))
        .as("exactly one row per pair, whichever order the requests arrived in")
        .hasSize(1);
  }

  @Test
  @DisplayName("self-requests and duplicate friendships are rejected")
  void invalidRequestsRejected() {
    assertThatThrownBy(() -> friendshipService.request(alice, alice))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.SELF_REQUEST);

    befriend(alice, bob);
    assertThatThrownBy(() -> friendshipService.request(alice, bob))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.ALREADY_FRIENDS);
  }

  @Test
  @DisplayName("unblocking restores no friendship")
  void unblockDoesNotRestoreFriendship() {
    befriend(alice, bob);
    friendshipService.block(alice, bob);
    friendshipService.unblock(alice, bob);

    assertThat(friendships.findBetween(alice, bob)).isEmpty();
    assertThat(friendships.findAccepted(alice)).isEmpty();
  }

  @Test
  @DisplayName("ranking is by score, highest first, with the viewer included")
  void rankingOrder() {
    befriend(alice, bob);
    seedScore(alice, 100_000L, 20_000L);
    seedScore(bob, 100_000L, 60_000L);

    ComparisonResponse view = comparisonService.compare(alice, MARCH);

    assertThat(view.ranked()).hasSize(2);
    assertThat(view.ranked().get(0).user().displayName()).isEqualTo("Bob");
    assertThat(view.ranked().get(0).rank()).isEqualTo(1);
    assertThat(view.ranked().get(1).user().displayName()).isEqualTo("Alice");
    assertThat(view.ranked().get(1).isSelf()).isTrue();
    assertThat(view.streakLookbackMonths()).isEqualTo(24);
  }

  @Test
  @DisplayName("a friend with no data is unranked, not zero and not last")
  void friendWithNoDataIsUnranked() {
    befriend(alice, bob);
    seedScore(alice, 100_000L, 20_000L);

    ComparisonResponse view = comparisonService.compare(alice, MARCH);

    assertThat(view.ranked()).hasSize(1);
    assertThat(view.unranked()).hasSize(1);
    assertThat(view.unranked().get(0).user().displayName()).isEqualTo("Bob");
    assertThat(view.unranked().get(0).reason()).isEqualTo(UnrankedReason.NO_DATA);
  }

  @Test
  @DisplayName("an unreconciled friend is excluded from ranking with that reason")
  void unreconciledFriendIsUnranked() {
    befriend(alice, bob);
    seedScore(alice, 100_000L, 20_000L);
    record(bob, EntryType.INCOME, 40_000L);
    record(bob, EntryType.EXPENSE, 90_000L);

    assertThat(reconciliation.view(bob, MARCH).state().isRankable()).isFalse();

    ComparisonResponse view = comparisonService.compare(alice, MARCH);

    assertThat(view.ranked()).extracting(e -> e.user().displayName()).containsExactly("Alice");
    assertThat(view.unranked().get(0).reason()).isEqualTo(UnrankedReason.UNRECONCILED);
  }

  @Test
  @DisplayName("when nobody is rankable the view still renders")
  void allUnrankedStillRenders() {
    befriend(alice, bob);

    ComparisonResponse view = comparisonService.compare(alice, MARCH);

    assertThat(view.ranked()).isEmpty();
    assertThat(view.unranked()).hasSize(2);
    assertThat(view.period()).isEqualTo("2026-03");
  }

  @Test
  @DisplayName("a streak counts consecutive complete months and resets on a gap")
  void streakCountsConsecutiveCompleteMonths() {
    seedScoreIn(alice, "2026-01-05", 100_000L, 10_000L);
    seedScoreIn(alice, "2026-02-05", 100_000L, 10_000L);
    seedScoreIn(alice, "2026-03-05", 100_000L, 10_000L);

    assertThat(comparisonService.compare(alice, MARCH).ranked().get(0).completeMonthStreak())
        .isEqualTo(3);

    // A month with entries but no income breaks the run.
    seedScoreIn(bob, "2026-01-05", 100_000L, 10_000L);
    record(bob, EntryType.EXPENSE, 5_000L, "2026-02-05");
    seedScoreIn(bob, "2026-03-05", 100_000L, 10_000L);

    assertThat(comparisonService.compare(bob, MARCH).ranked().get(0).completeMonthStreak())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("change versus the previous month is reported, and is null when there is none")
  void changeFromPreviousPeriod() {
    seedScoreIn(alice, "2026-02-05", 100_000L, 10_000L);
    seedScoreIn(alice, "2026-03-05", 100_000L, 30_000L);

    assertThat(comparisonService.compare(alice, MARCH).ranked().get(0)
        .changeFromPreviousPeriod()).isEqualTo(20);

    seedScoreIn(bob, "2026-03-05", 100_000L, 30_000L);
    assertThat(comparisonService.compare(bob, MARCH).ranked().get(0)
        .changeFromPreviousPeriod())
        .as("null, not zero: there is nothing to compare against")
        .isNull();
  }

  private void befriend(UUID left, UUID right) {
    friendshipService.request(left, right);
    Friendship pending = friendships.findBetween(left, right).orElseThrow();
    friendshipService.accept(right, pending.getId());
  }

  private UUID newUser(String displayName) {
    return users.save(AppUser.register(
        displayName.toLowerCase(java.util.Locale.ROOT) + "-" + UUID.randomUUID()
            + "@example.com",
        "hash", displayName, INR, "Asia/Kolkata", clock.instant()))
        .getId();
  }

  private void seedScore(UUID userId, long income, long saved) {
    seedScoreIn(userId, "2026-03-05", income, saved);
  }

  private void seedScoreIn(UUID userId, String date, long income, long saved) {
    record(userId, EntryType.INCOME, income, date);
    record(userId, EntryType.SAVING, saved, date);
  }

  private void record(UUID userId, EntryType type, long minor) {
    record(userId, type, minor, "2026-03-05");
  }

  private void record(UUID userId, EntryType type, long minor, String date) {
    List<Category> available = categories.findVisibleToByType(userId, type);
    entryService.create(userId, new CreateEntryRequest(
        type, Direction.CONTRIBUTION, new MoneyDto(minor, "INR"),
        LocalDate.parse(date), available.get(0).getId(), null));
  }
}
