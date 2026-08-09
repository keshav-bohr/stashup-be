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
import com.stashup.domain.PeriodRef;
import com.stashup.dto.ComparisonDtos.ComparisonResponse;
import com.stashup.dto.EntryDtos.CreateEntryRequest;
import com.stashup.dto.EntryDtos.MoneyDto;
import com.stashup.dto.FriendshipDtos.FriendRequestResponse;
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
import com.stashup.service.FriendVisibilityService;
import com.stashup.service.FriendshipService;
import com.stashup.support.MySqlTestBase;

/** The friendship paths that are easy to get wrong and easy to leave untested. */
class FriendshipEdgeCaseIT extends MySqlTestBase {

  private static final Currency INR = Currency.getInstance("INR");

  @Autowired private FriendshipService friendshipService;
  @Autowired private FriendVisibilityService visibility;
  @Autowired private FriendshipRepository friendships;
  @Autowired private ComparisonService comparisonService;
  @Autowired private EntryService entryService;
  @Autowired private AppUserRepository users;
  @Autowired private CategoryRepository categories;
  @Autowired private Clock clock;

  private UUID alice;
  private UUID bob;
  private UUID carol;

  @BeforeEach
  void seed() {
    alice = newUser("Alice");
    bob = newUser("Bob");
    carol = newUser("Carol");
  }

  @Test
  @DisplayName("blocking yourself is rejected")
  void selfBlockRejected() {
    assertThat(codeOf(() -> friendshipService.block(alice, alice)))
        .isEqualTo(ErrorCode.SELF_REQUEST);
  }

  @Test
  @DisplayName("blocking an unknown user is a 404")
  void blockingUnknownUser() {
    assertThat(codeOf(() -> friendshipService.block(alice, UUID.randomUUID())))
        .isEqualTo(ErrorCode.NOT_FOUND);
    assertThat(codeOf(() -> friendshipService.request(alice, UUID.randomUUID())))
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("only the blocker can unblock, and only an existing block")
  void unblockIsRestricted() {
    friendshipService.block(alice, bob);

    assertThat(codeOf(() -> friendshipService.unblock(bob, alice)))
        .as("the blocked party must not be able to lift the block")
        .isEqualTo(ErrorCode.NOT_FOUND);
    assertThat(codeOf(() -> friendshipService.unblock(alice, carol)))
        .isEqualTo(ErrorCode.NOT_FOUND);

    friendshipService.unblock(alice, bob);
    assertThat(friendships.findBetween(alice, bob)).isEmpty();
  }

  @Test
  @DisplayName("removing someone who is not a friend is a 404")
  void removingANonFriend() {
    assertThat(codeOf(() -> friendshipService.removeFriend(alice, bob)))
        .isEqualTo(ErrorCode.NOT_FOUND);

    friendshipService.request(alice, bob);
    assertThat(codeOf(() -> friendshipService.removeFriend(alice, bob)))
        .as("a pending request is not a friendship")
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("only the recipient can accept or decline, and only once")
  void acceptanceIsRestrictedToTheRecipient() {
    friendshipService.request(alice, bob);
    UUID requestId = friendships.findBetween(alice, bob).orElseThrow().getId();

    assertThat(codeOf(() -> friendshipService.accept(alice, requestId)))
        .as("the requester cannot accept their own request")
        .isEqualTo(ErrorCode.NOT_FOUND);
    assertThat(codeOf(() -> friendshipService.accept(carol, requestId)))
        .isEqualTo(ErrorCode.NOT_FOUND);

    friendshipService.accept(bob, requestId);

    assertThat(codeOf(() -> friendshipService.accept(bob, requestId)))
        .as("an accepted request is no longer pending")
        .isEqualTo(ErrorCode.NOT_FOUND);
    assertThat(codeOf(() -> friendshipService.decline(bob, requestId)))
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("re-requesting an outstanding request of your own is a no-op")
  void repeatedRequestIsIdempotent() {
    friendshipService.request(alice, bob);
    friendshipService.request(alice, bob);

    assertThat(friendships.findAllInvolving(alice)).hasSize(1);
    assertThat(friendshipService.pendingRequests(alice, false)).hasSize(1);
    assertThat(friendshipService.pendingRequests(alice, true)).isEmpty();
  }

  @Test
  @DisplayName("blocking replaces an existing friendship rather than adding a row")
  void blockingReplacesTheExistingRow() {
    friendshipService.request(alice, bob);
    friendshipService.accept(bob, friendships.findBetween(alice, bob).orElseThrow().getId());

    friendshipService.block(bob, alice);

    assertThat(friendships.findAllInvolving(alice)).hasSize(1);
    assertThat(friendships.findAccepted(alice)).isEmpty();
    assertThat(visibility.statusLabel(alice, bob))
        .as("a block is reported as no relationship, so it stays undetectable")
        .isEqualTo("NONE");
  }

  @Test
  @DisplayName("status labels distinguish incoming from outgoing requests")
  void statusLabelsReflectDirection() {
    friendshipService.request(alice, bob);

    assertThat(visibility.statusLabel(alice, bob)).isEqualTo("PENDING_OUTGOING");
    assertThat(visibility.statusLabel(bob, alice)).isEqualTo("PENDING_INCOMING");
    assertThat(visibility.statusLabel(alice, carol)).isEqualTo("NONE");

    friendshipService.accept(bob, friendships.findBetween(alice, bob).orElseThrow().getId());
    assertThat(visibility.statusLabel(alice, bob)).isEqualTo("ACCEPTED");
  }

  @Test
  @DisplayName("the request DTO reports who asked whom")
  void requestDtoCarriesDirection() {
    Friendship friendship = friendshipService.request(alice, bob);

    FriendRequestResponse response = FriendRequestResponse.from(friendship);

    assertThat(response.fromUserId()).isEqualTo(alice);
    assertThat(response.toUserId()).isEqualTo(bob);
    assertThat(response.createdAt()).isNotNull();
  }

  @Test
  @DisplayName("a yearly comparison aggregates each participant's months")
  void yearlyComparison() {
    befriend(alice, bob);
    record(alice, EntryType.INCOME, 1_000L, "2026-01-05");
    record(alice, EntryType.SAVING, 500L, "2026-01-06");
    record(alice, EntryType.INCOME, 100_000L, "2026-02-05");
    record(alice, EntryType.SAVING, 5_000L, "2026-02-06");
    record(bob, EntryType.INCOME, 100_000L, "2026-02-05");
    record(bob, EntryType.SAVING, 30_000L, "2026-02-06");

    ComparisonResponse view = comparisonService.compare(alice, PeriodRef.parse("2026"));

    assertThat(view.period()).isEqualTo("2026");
    assertThat(view.ranked()).hasSize(2);
    assertThat(view.ranked().get(0).user().id()).isEqualTo(bob);
    assertThat(view.ranked().get(0).score()).isEqualTo((short) 30);
    assertThat(view.ranked().get(1).score())
        .as("5500 stashed of 101000 in, not the average of 50 and 5")
        .isEqualTo((short) 5);
  }

  private void befriend(UUID left, UUID right) {
    friendshipService.request(left, right);
    friendshipService.accept(right, friendships.findBetween(left, right).orElseThrow().getId());
  }

  private UUID newUser(String displayName) {
    return users.save(AppUser.register(
        displayName.toLowerCase(java.util.Locale.ROOT) + "-" + UUID.randomUUID()
            + "@example.com",
        "hash", displayName, INR, "Asia/Kolkata", clock.instant()))
        .getId();
  }

  private void record(UUID userId, EntryType type, long minor, String date) {
    List<Category> available = categories.findVisibleToByType(userId, type);
    entryService.create(userId, new CreateEntryRequest(
        type, Direction.CONTRIBUTION, new MoneyDto(minor, "INR"),
        LocalDate.parse(date), available.get(0).getId(), null));
  }

  private static ErrorCode codeOf(Runnable action) {
    return assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ErrorCode.class))
        .actual();
  }
}
