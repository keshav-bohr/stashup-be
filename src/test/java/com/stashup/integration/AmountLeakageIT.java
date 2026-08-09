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
import com.stashup.dto.ComparisonDtos.ComparisonEntry;
import com.stashup.dto.ComparisonDtos.ComparisonResponse;
import com.stashup.dto.EntryDtos.CreateEntryRequest;
import com.stashup.dto.EntryDtos.MoneyDto;
import com.stashup.entity.AppUser;
import com.stashup.entity.Category;
import com.stashup.entity.Friendship;
import com.stashup.repository.AppUserRepository;
import com.stashup.repository.CategoryRepository;
import com.stashup.repository.FriendshipRepository;
import com.stashup.service.ComparisonService;
import com.stashup.service.EntryService;
import com.stashup.service.FriendshipService;
import com.stashup.service.UserService;
import com.stashup.support.MySqlTestBase;

import tools.jackson.databind.ObjectMapper;

/**
 * FR-037: no amount, income, category, or reconciliation gap of one user may reach another —
 * including between accepted friends.
 *
 * <p>The strongest assertion here is the serialised sweep: the friend's distinctive amounts are
 * seeded as recognisable numbers, and the rendered JSON of every friend-visible response is
 * searched for them. That catches a leak introduced by adding a field to a DTO, which a
 * field-by-field assertion would silently miss.
 */
class AmountLeakageIT extends MySqlTestBase {

  private static final Currency INR = Currency.getInstance("INR");
  private static final PeriodRef MARCH = PeriodRef.parse("2026-03");

  /** Deliberately distinctive so a substring search cannot match them by accident. */
  private static final long FRIEND_INCOME = 777_777_701L;
  private static final long FRIEND_SAVING = 314_159_203L;
  private static final long FRIEND_EXPENSE = 271_828_307L;

  @Autowired private EntryService entryService;
  @Autowired private ComparisonService comparisonService;
  @Autowired private FriendshipService friendshipService;
  @Autowired private FriendshipRepository friendships;
  @Autowired private UserService userService;
  @Autowired private AppUserRepository users;
  @Autowired private CategoryRepository categories;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private Clock clock;

  private UUID viewer;
  private UUID friend;

  @BeforeEach
  void seed() {
    viewer = newUser("Viewer");
    friend = newUser("Friend");

    record(viewer, EntryType.INCOME, 100_000L);
    record(viewer, EntryType.SAVING, 10_000L);

    record(friend, EntryType.INCOME, FRIEND_INCOME);
    record(friend, EntryType.SAVING, FRIEND_SAVING);
    record(friend, EntryType.EXPENSE, FRIEND_EXPENSE);

    friendshipService.request(viewer, friend);
    Friendship pending = friendships.findBetween(viewer, friend).orElseThrow();
    friendshipService.accept(friend, pending.getId());
  }

  @Test
  @DisplayName("no friend amount appears anywhere in the serialised comparison view")
  void comparisonViewLeaksNoAmounts() {
    ComparisonResponse view = comparisonService.compare(viewer, MARCH);
    String json = objectMapper.writeValueAsString(view);

    assertThat(json).doesNotContain(String.valueOf(FRIEND_INCOME));
    assertThat(json).doesNotContain(String.valueOf(FRIEND_SAVING));
    assertThat(json).doesNotContain(String.valueOf(FRIEND_EXPENSE));
    // Nor any of the vocabulary an amount would travel under.
    assertThat(json)
        .doesNotContain("amountMinor")
        .doesNotContain("moneyIn")
        .doesNotContain("stashed")
        .doesNotContain("gap")
        .doesNotContain("outflow")
        .doesNotContain("categor");
  }

  @Test
  @DisplayName("the comparison entry exposes a closed, reviewed set of fields")
  void comparisonEntryFieldSetIsClosed() {
    // Adding a field here is the most likely way this product leaks financial data, so the shape
    // is asserted explicitly: a new component fails this test and forces a deliberate decision.
    assertThat(ComparisonEntry.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .containsExactlyInAnyOrder(
            "user", "isSelf", "rank", "score", "band",
            "changeFromPreviousPeriod", "completeMonthStreak");
  }

  @Test
  @DisplayName("search results leak no amounts")
  void searchLeaksNoAmounts() {
    String json = objectMapper.writeValueAsString(userService.search(viewer, "Friend", 10));

    assertThat(json).doesNotContain(String.valueOf(FRIEND_INCOME));
    assertThat(json).doesNotContain(String.valueOf(FRIEND_SAVING));
    assertThat(json).doesNotContain("amountMinor").doesNotContain("score");
  }

  @Test
  @DisplayName("the viewer's own comparison row also carries no amounts")
  void ownRowCarriesNoAmountsEither() {
    ComparisonResponse view = comparisonService.compare(viewer, MARCH);
    String json = objectMapper.writeValueAsString(view);

    assertThat(json)
        .as("the comparison view is a score surface, not a financial one, even for oneself")
        .doesNotContain("100000")
        .doesNotContain("amountMinor");
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
