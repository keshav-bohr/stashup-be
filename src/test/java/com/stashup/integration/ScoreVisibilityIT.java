package com.stashup.integration;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.stashup.domain.PeriodRef;
import com.stashup.dto.ComparisonDtos.ComparisonResponse;
import com.stashup.dto.EntryDtos.CreateEntryRequest;
import com.stashup.dto.EntryDtos.MoneyDto;
import com.stashup.dto.UserDtos.UserSummary;
import com.stashup.entity.AppUser;
import com.stashup.entity.Category;
import com.stashup.entity.Friendship;
import com.stashup.repository.AppUserRepository;
import com.stashup.repository.CategoryRepository;
import com.stashup.repository.FriendshipRepository;
import com.stashup.service.ComparisonService;
import com.stashup.service.EntryService;
import com.stashup.service.FriendVisibilityService;
import com.stashup.service.FriendshipService;
import com.stashup.service.UserService;
import com.stashup.support.MySqlTestBase;

/**
 * FR-036: a user's score and band are visible only to that user and their accepted friends.
 *
 * <p>Every other relationship — no relationship, a pending request, a block, a removed friendship
 * — must produce exactly the same outcome: nothing. This is the highest-consequence guarantee in
 * the product, so each state gets its own assertion rather than being covered by one happy path.
 */
class ScoreVisibilityIT extends MySqlTestBase {

  private static final Currency INR = Currency.getInstance("INR");
  private static final PeriodRef MARCH = PeriodRef.parse("2026-03");

  @Autowired private EntryService entryService;
  @Autowired private ComparisonService comparisonService;
  @Autowired private FriendshipService friendshipService;
  @Autowired private FriendVisibilityService visibility;
  @Autowired private FriendshipRepository friendships;
  @Autowired private UserService userService;
  @Autowired private AppUserRepository users;
  @Autowired private CategoryRepository categories;
  @Autowired private Clock clock;

  private UUID alice;
  private UUID bob;

  /**
   * Display names are unique per test. The database is shared across tests in this class, so
   * fixed names like "Alice" would match users seeded by earlier tests and make search
   * assertions meaningless.
   */
  private String aliceName;
  private String bobName;

  @BeforeEach
  void seed() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    aliceName = "Alice" + suffix;
    bobName = "Bob" + suffix;
    alice = newUser(aliceName);
    bob = newUser(bobName);
    seedScore(alice, 100_000L, 40_000L);
    seedScore(bob, 100_000L, 20_000L);
  }

  @Test
  @DisplayName("strangers cannot see each other's score")
  void strangersSeeNothing() {
    assertThat(visibility.canSeeScoreOf(alice, bob)).isFalse();

    ComparisonResponse view = comparisonService.compare(alice, MARCH);

    assertThat(participantIds(view)).containsExactly(alice);
  }

  @Test
  @DisplayName("a pending request confers no visibility")
  void pendingRequestConfersNothing() {
    friendshipService.request(alice, bob);

    assertThat(visibility.canSeeScoreOf(alice, bob)).isFalse();
    assertThat(visibility.canSeeScoreOf(bob, alice)).isFalse();
    assertThat(participantIds(comparisonService.compare(alice, MARCH))).containsExactly(alice);
  }

  @Test
  @DisplayName("accepted friends see each other's score and band")
  void acceptedFriendsSeeScores() {
    befriend(alice, bob);

    assertThat(visibility.canSeeScoreOf(alice, bob)).isTrue();
    assertThat(visibility.canSeeScoreOf(bob, alice)).isTrue();

    ComparisonResponse view = comparisonService.compare(alice, MARCH);

    assertThat(view.ranked()).hasSize(2);
    assertThat(view.ranked().get(0).score()).isEqualTo((short) 40);
    assertThat(view.ranked().get(0).band()).isNotNull();
    assertThat(view.ranked().get(1).score()).isEqualTo((short) 20);
  }

  @Test
  @DisplayName("removing a friend revokes visibility in both directions")
  void removalRevokesVisibility() {
    befriend(alice, bob);
    friendshipService.removeFriend(alice, bob);

    assertThat(visibility.canSeeScoreOf(alice, bob)).isFalse();
    assertThat(visibility.canSeeScoreOf(bob, alice)).isFalse();
    assertThat(participantIds(comparisonService.compare(bob, MARCH))).containsExactly(bob);
  }

  @Test
  @DisplayName("blocking revokes visibility and hides both users from each other")
  void blockingRevokesVisibilityBothWays() {
    befriend(alice, bob);
    friendshipService.block(alice, bob);

    assertThat(visibility.canSeeScoreOf(alice, bob)).isFalse();
    assertThat(visibility.canSeeScoreOf(bob, alice)).isFalse();

    assertThat(userService.search(bob, aliceName, 10)).isEmpty();
    assertThat(userService.search(alice, bobName, 10)).isEmpty();
  }

  @Test
  @DisplayName("a blocked user cannot detect the block by probing")
  void blockIsNotDetectable() {
    friendshipService.block(alice, bob);

    // Bob sees no relationship at all, rather than a state that reveals he was blocked.
    List<UserSummary> results = userService.search(bob, aliceName, 10);
    assertThat(results).isEmpty();

    // Re-requesting succeeds silently instead of returning a distinguishing error.
    friendshipService.request(bob, alice);
    assertThat(visibility.canSeeScoreOf(bob, alice)).isFalse();
  }

  @Test
  @DisplayName("search never carries a score, even between friends")
  void searchCarriesIdentityOnly() {
    befriend(alice, bob);

    List<UserSummary> results = userService.search(alice, bobName, 10);

    assertThat(results).hasSize(1);
    UserSummary summary = results.get(0);
    assertThat(summary.displayName()).isEqualTo(bobName);
    assertThat(summary.friendshipStatus()).isEqualTo("ACCEPTED");
    // UserSummary has no score field at all — the type makes the leak impossible, and this
    // assertion documents that the record's component set is intentional.
    assertThat(UserSummary.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .containsExactlyInAnyOrder("id", "displayName", "friendshipStatus");
  }

  private void befriend(UUID left, UUID right) {
    friendshipService.request(left, right);
    Friendship pending = friendships.findBetween(left, right).orElseThrow();
    friendshipService.accept(right, pending.getId());
  }

  private static List<UUID> participantIds(ComparisonResponse view) {
    return java.util.stream.Stream.concat(
            view.ranked().stream().map(e -> e.user().id()),
            view.unranked().stream().map(e -> e.user().id()))
        .toList();
  }

  private UUID newUser(String displayName) {
    return users.save(AppUser.register(
        displayName.toLowerCase(java.util.Locale.ROOT) + "-" + UUID.randomUUID()
            + "@example.com",
        "hash", displayName, INR, "Asia/Kolkata", clock.instant()))
        .getId();
  }

  private void seedScore(UUID userId, long income, long saved) {
    record(userId, EntryType.INCOME, income);
    record(userId, EntryType.SAVING, saved);
  }

  private void record(UUID userId, EntryType type, long minor) {
    List<Category> available = categories.findVisibleToByType(userId, type);
    entryService.create(userId, new CreateEntryRequest(
        type, Direction.CONTRIBUTION, new MoneyDto(minor, "INR"),
        LocalDate.parse("2026-03-05"), available.get(0).getId(), null));
  }
}
