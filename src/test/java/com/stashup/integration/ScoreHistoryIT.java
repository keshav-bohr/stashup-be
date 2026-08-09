package com.stashup.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
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
import com.stashup.dto.CategoryDtos.CategoryResponse;
import com.stashup.dto.CategoryDtos.CreateCategoryRequest;
import com.stashup.dto.EntryDtos.CreateEntryRequest;
import com.stashup.dto.EntryDtos.MoneyDto;
import com.stashup.dto.ScoreDtos.ScoreResponse;
import com.stashup.entity.AppUser;
import com.stashup.entity.Category;
import com.stashup.exception.ApiException;
import com.stashup.exception.ErrorCode;
import com.stashup.repository.AppUserRepository;
import com.stashup.repository.CategoryRepository;
import com.stashup.service.CategoryService;
import com.stashup.service.EntryService;
import com.stashup.service.ScoreService;
import com.stashup.support.MySqlTestBase;

/** Score history over a range of months, plus category management around it. */
class ScoreHistoryIT extends MySqlTestBase {

  private static final Currency INR = Currency.getInstance("INR");

  @Autowired private EntryService entryService;
  @Autowired private ScoreService scoreService;
  @Autowired private CategoryService categoryService;
  @Autowired private AppUserRepository users;
  @Autowired private CategoryRepository categories;
  @Autowired private Clock clock;

  private UUID userId;

  @BeforeEach
  void createUser() {
    userId = users.save(AppUser.register(
        "history-" + UUID.randomUUID() + "@example.com",
        "hash", "History User", INR, "Asia/Kolkata", clock.instant()))
        .getId();
  }

  @Test
  @DisplayName("history returns one entry per month with data, oldest first")
  void historyIsChronological() {
    seed("2026-01-05", 100_000L, 10_000L);
    seed("2026-02-05", 100_000L, 20_000L);
    seed("2026-04-05", 100_000L, 40_000L);

    List<ScoreResponse> history =
        scoreService.history(userId, YearMonth.of(2026, 1), YearMonth.of(2026, 4));

    assertThat(history).extracting(ScoreResponse::period)
        .containsExactly("2026-01", "2026-02", "2026-04");
    assertThat(history).extracting(ScoreResponse::score)
        .containsExactly((short) 10, (short) 20, (short) 40);
  }

  @Test
  @DisplayName("month-over-month change is present only where a prior month exists")
  void changeIsPresentOnlyWhereComparable() {
    seed("2026-01-05", 100_000L, 10_000L);
    seed("2026-02-05", 100_000L, 25_000L);

    List<ScoreResponse> history =
        scoreService.history(userId, YearMonth.of(2026, 1), YearMonth.of(2026, 2));

    assertThat(history.get(0).changeFromPreviousPeriod())
        .as("nothing precedes January here, so there is nothing to compare against")
        .isNull();
    assertThat(history.get(1).changeFromPreviousPeriod()).isEqualTo(15);
  }

  @Test
  @DisplayName("an empty range returns an empty history rather than failing")
  void emptyRangeIsEmpty() {
    assertThat(scoreService.history(userId, YearMonth.of(2025, 1), YearMonth.of(2025, 6)))
        .isEmpty();
  }

  @Test
  @DisplayName("a period with no data has no score to return")
  void missingPeriodIsNotFound() {
    assertThatThrownBy(() -> scoreService.score(userId, PeriodRef.parse("2025-07")))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("user-defined categories work alongside the seeded system ones")
  void userDefinedCategories() {
    CategoryResponse created = categoryService.create(
        userId, new CreateCategoryRequest(EntryType.EXPENSE, "Cricket Tickets"));

    assertThat(created.system()).isFalse();
    assertThat(categoryService.list(userId, EntryType.EXPENSE))
        .extracting(CategoryResponse::name)
        .contains("Cricket Tickets", "Groceries");

    assertThatThrownBy(() -> categoryService.create(
        userId, new CreateCategoryRequest(EntryType.EXPENSE, "Cricket Tickets")))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.DUPLICATE_CATEGORY);
  }

  @Test
  @DisplayName("a category still in use cannot be deleted")
  void categoryInUseCannotBeDeleted() {
    CategoryResponse created = categoryService.create(
        userId, new CreateCategoryRequest(EntryType.EXPENSE, "Season Pass"));
    entryService.create(userId, new CreateEntryRequest(
        EntryType.EXPENSE, Direction.CONTRIBUTION, new MoneyDto(500L, "INR"),
        LocalDate.parse("2026-03-05"), created.id(), null));

    assertThatThrownBy(() -> categoryService.delete(userId, created.id()))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.CATEGORY_IN_USE);
  }

  @Test
  @DisplayName("an entry cannot use a category belonging to a different type")
  void categoryTypeMustMatch() {
    UUID savingCategory =
        categories.findVisibleToByType(userId, EntryType.SAVING).get(0).getId();

    assertThatThrownBy(() -> entryService.create(userId, new CreateEntryRequest(
        EntryType.EXPENSE, Direction.CONTRIBUTION, new MoneyDto(500L, "INR"),
        LocalDate.parse("2026-03-05"), savingCategory, null)))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.CATEGORY_TYPE_MISMATCH);
  }

  private void seed(String date, long income, long saved) {
    record(EntryType.INCOME, income, date);
    record(EntryType.SAVING, saved, date);
  }

  private void record(EntryType type, long minor, String date) {
    List<Category> available = categories.findVisibleToByType(userId, type);
    entryService.create(userId, new CreateEntryRequest(
        type, Direction.CONTRIBUTION, new MoneyDto(minor, "INR"),
        LocalDate.parse(date), available.get(0).getId(), null));
  }
}
