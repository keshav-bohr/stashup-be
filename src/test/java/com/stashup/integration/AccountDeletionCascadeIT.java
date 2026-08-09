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
import com.stashup.dto.EntryDtos.CreateEntryRequest;
import com.stashup.dto.EntryDtos.MoneyDto;
import com.stashup.entity.AppUser;
import com.stashup.entity.Category;
import com.stashup.entity.Friendship;
import com.stashup.repository.AppUserRepository;
import com.stashup.repository.CategoryRepository;
import com.stashup.repository.FriendshipRepository;
import com.stashup.repository.PeriodSummaryRepository;
import com.stashup.service.ComparisonService;
import com.stashup.service.EntryService;
import com.stashup.service.FriendshipService;
import com.stashup.service.UserService;
import com.stashup.support.MySqlTestBase;

/**
 * FR-003: deleting an account removes the user's data and removes them from every other user's
 * friend list and comparison view.
 *
 * <p>Hard delete, not soft — the requirement is that the data is gone, so a tombstone row would
 * be the wrong answer. The cascade is a schema property (every owned table has
 * {@code ON DELETE CASCADE} from {@code app_user}), and this test asserts it actually holds
 * rather than trusting the DDL.
 */
class AccountDeletionCascadeIT extends MySqlTestBase {

  private static final Currency INR = Currency.getInstance("INR");
  private static final PeriodRef MARCH = PeriodRef.parse("2026-03");

  @Autowired private UserService userService;
  @Autowired private EntryService entryService;
  @Autowired private FriendshipService friendshipService;
  @Autowired private ComparisonService comparisonService;
  @Autowired private AppUserRepository users;
  @Autowired private FriendshipRepository friendships;
  @Autowired private PeriodSummaryRepository summaries;
  @Autowired private CategoryRepository categories;
  @Autowired private Clock clock;

  private UUID leaver;
  private UUID stayer;

  @BeforeEach
  void seed() {
    leaver = newUser("Leaver");
    stayer = newUser("Stayer");

    record(leaver, EntryType.INCOME, 100_000L);
    record(leaver, EntryType.SAVING, 30_000L);
    record(stayer, EntryType.INCOME, 100_000L);
    record(stayer, EntryType.SAVING, 10_000L);

    friendshipService.request(leaver, stayer);
    Friendship pending = friendships.findBetween(leaver, stayer).orElseThrow();
    friendshipService.accept(stayer, pending.getId());
  }

  @Test
  @DisplayName("deleting an account removes its entries, summaries, and friendships")
  void deletionRemovesOwnedData() {
    assertThat(summaries.findByUserIdAndPeriodStart(leaver, MARCH.start())).isPresent();
    assertThat(friendships.findAccepted(stayer)).hasSize(1);

    userService.deleteAccount(leaver);

    assertThat(users.findById(leaver)).isEmpty();
    assertThat(summaries.findByUserIdAndPeriodStart(leaver, MARCH.start())).isEmpty();
    assertThat(friendships.findBetween(leaver, stayer))
        .as("the friendship row must go with the account, leaving no orphan")
        .isEmpty();
  }

  @Test
  @DisplayName("a deleted user disappears from other users' comparison views")
  void deletedUserVanishesFromComparison() {
    assertThat(comparisonService.compare(stayer, MARCH).ranked()).hasSize(2);

    userService.deleteAccount(leaver);

    assertThat(comparisonService.compare(stayer, MARCH).ranked())
        .extracting(entry -> entry.user().id())
        .containsExactly(stayer);
    assertThat(friendships.findAccepted(stayer)).isEmpty();
  }

  @Test
  @DisplayName("the remaining user's own data is untouched")
  void otherUsersDataSurvives() {
    userService.deleteAccount(leaver);

    assertThat(users.findById(stayer)).isPresent();
    assertThat(summaries.findByUserIdAndPeriodStart(stayer, MARCH.start())).isPresent();
  }

  private UUID newUser(String displayName) {
    return users.save(AppUser.register(
        displayName.toLowerCase(java.util.Locale.ROOT) + "-" + UUID.randomUUID()
            + "@example.com",
        "hash", displayName, INR, "Asia/Kolkata", clock.instant()))
        .getId();
  }

  private void record(UUID userId, EntryType type, long minor) {
    List<Category> available = categories.findVisibleToByType(userId, type);
    entryService.create(userId, new CreateEntryRequest(
        type, Direction.CONTRIBUTION, new MoneyDto(minor, "INR"),
        LocalDate.parse("2026-03-05"), available.get(0).getId(), null));
  }
}
