package com.stashup.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stashup.domain.Direction;
import com.stashup.domain.EntryType;
import com.stashup.domain.Money;
import com.stashup.dto.CategoryDtos.CategoryResponse;
import com.stashup.dto.EntryDtos.CreateEntryRequest;
import com.stashup.dto.EntryDtos.EntryResponse;
import com.stashup.dto.EntryDtos.UpdateEntryRequest;
import com.stashup.entity.AppUser;
import com.stashup.entity.Category;
import com.stashup.entity.FinancialEntry;
import com.stashup.exception.ApiException;
import com.stashup.repository.AppUserRepository;
import com.stashup.repository.CategoryRepository;
import com.stashup.repository.FinancialEntryRepository;
import com.stashup.web.CursorCodec;
import com.stashup.web.KeysetPage;

/**
 * Recording, listing, editing, and deleting entries.
 *
 * <p>Every mutation recomputes the affected month's summary in the same transaction, and an edit
 * that moves an entry across a month boundary recomputes both months — otherwise the source month
 * would keep counting an entry it no longer contains.
 */
@Service
public class EntryService {

  private final FinancialEntryRepository entries;
  private final CategoryRepository categories;
  private final CategoryService categoryService;
  private final AppUserRepository users;
  private final EntryValidator validator;
  private final PeriodSummaryRecomputeService recompute;
  private final Clock clock;

  public EntryService(
      FinancialEntryRepository entries,
      CategoryRepository categories,
      CategoryService categoryService,
      AppUserRepository users,
      EntryValidator validator,
      PeriodSummaryRecomputeService recompute,
      Clock clock) {
    this.entries = entries;
    this.categories = categories;
    this.categoryService = categoryService;
    this.users = users;
    this.validator = validator;
    this.recompute = recompute;
    this.clock = clock;
  }

  @Transactional
  public EntryResponse create(UUID userId, CreateEntryRequest request) {
    AppUser user = requireUser(userId);
    Direction direction = request.directionOrDefault();
    Money amount = request.amount().toMoney();

    validator.validate(
        request.entryType(),
        direction,
        amount,
        request.entryDate(),
        user.getBaseCurrency(),
        user.getTimezone());

    Category category =
        categoryService.requireUsable(userId, request.categoryId(), request.entryType());

    Instant now = clock.instant();
    FinancialEntry entry = entries.save(FinancialEntry.record(
        userId,
        request.entryType(),
        direction,
        amount,
        request.entryDate(),
        category.getId(),
        request.note(),
        now));

    recompute.recompute(userId, YearMonth.from(entry.getEntryDate()), user.getBaseCurrency());
    return EntryResponse.of(entry, CategoryResponse.from(category));
  }

  @Transactional(readOnly = true)
  public EntryResponse get(UUID userId, UUID entryId) {
    FinancialEntry entry =
        entries.findByIdAndUserId(entryId, userId).orElseThrow(ApiException::notFound);
    return EntryResponse.of(entry, categoryResponse(userId, entry.getCategoryId()));
  }

  @Transactional(readOnly = true)
  public KeysetPage<EntryResponse> list(
      UUID userId,
      @Nullable LocalDate from,
      @Nullable LocalDate to,
      @Nullable EntryType entryType,
      @Nullable UUID categoryId,
      @Nullable String cursor,
      int limit) {

    CursorCodec.@Nullable Cursor position = cursor == null ? null : CursorCodec.decode(cursor);

    // Fetch one extra row to learn whether another page exists without a second count query.
    List<FinancialEntry> found = entries.findPage(
        userId,
        from,
        to,
        entryType,
        categoryId,
        position == null ? null : position.entryDate(),
        position == null ? null : position.id(),
        Limit.of(limit + 1));

    boolean hasMore = found.size() > limit;
    List<FinancialEntry> page = hasMore ? found.subList(0, limit) : found;

    Map<UUID, CategoryResponse> categoryCache = categoryCache(userId);
    List<EntryResponse> items = page.stream()
        .map(entry -> EntryResponse.of(entry, categoryCache.get(entry.getCategoryId())))
        .toList();

    if (!hasMore || page.isEmpty()) {
      return KeysetPage.last(items);
    }
    FinancialEntry lastRow = page.get(page.size() - 1);
    return new KeysetPage<>(
        items, CursorCodec.encode(lastRow.getEntryDate(), lastRow.getId()));
  }

  @Transactional
  public EntryResponse update(UUID userId, UUID entryId, UpdateEntryRequest request) {
    AppUser user = requireUser(userId);
    FinancialEntry entry =
        entries.findByIdAndUserId(entryId, userId).orElseThrow(ApiException::notFound);

    YearMonth originalMonth = YearMonth.from(entry.getEntryDate());
    Instant now = clock.instant();

    Money amount = request.amount() == null ? entry.getAmount() : request.amount().toMoney();
    LocalDate entryDate = request.entryDate() == null ? entry.getEntryDate() : request.entryDate();
    Direction direction = request.direction() == null ? entry.getDirection() : request.direction();

    validator.validate(
        entry.getEntryType(), direction, amount, entryDate, user.getBaseCurrency(),
        user.getTimezone());

    Category category = request.categoryId() == null
        ? categories.findVisibleToById(userId, entry.getCategoryId())
            .orElseThrow(ApiException::notFound)
        : categoryService.requireUsable(userId, request.categoryId(), entry.getEntryType());

    entry.changeAmount(amount, now);
    entry.changeDate(entryDate, now);
    entry.changeDirection(direction, now);
    entry.changeCategory(category.getId(), now);
    if (request.note() != null) {
      entry.changeNote(request.note(), now);
    }

    // Both months, so the source month stops counting an entry it no longer holds.
    recompute.recomputeBoth(
        userId, originalMonth, YearMonth.from(entryDate), user.getBaseCurrency());
    return EntryResponse.of(entry, CategoryResponse.from(category));
  }

  @Transactional
  public void delete(UUID userId, UUID entryId) {
    AppUser user = requireUser(userId);
    FinancialEntry entry =
        entries.findByIdAndUserId(entryId, userId).orElseThrow(ApiException::notFound);
    YearMonth month = YearMonth.from(entry.getEntryDate());
    entries.delete(entry);
    entries.flush();
    recompute.recompute(userId, month, user.getBaseCurrency());
  }

  private AppUser requireUser(UUID userId) {
    return users.findById(userId).orElseThrow(ApiException::notFound);
  }

  private Map<UUID, CategoryResponse> categoryCache(UUID userId) {
    return categories.findVisibleTo(userId).stream()
        .collect(java.util.stream.Collectors.toMap(
            Category::getId, CategoryResponse::from, (left, right) -> left));
  }

  private CategoryResponse categoryResponse(UUID userId, UUID categoryId) {
    return categories
        .findVisibleToById(userId, categoryId)
        .map(CategoryResponse::from)
        .orElseThrow(ApiException::notFound);
  }

  /** Exposed for the summary layer, which needs the same category lookup. */
  public Function<UUID, CategoryResponse> categoryResolver(UUID userId) {
    Map<UUID, CategoryResponse> cache = categoryCache(userId);
    return cache::get;
  }
}
