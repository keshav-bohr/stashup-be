package com.stashup.category;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stashup.category.CategoryDtos.CategoryResponse;
import com.stashup.category.CategoryDtos.CreateCategoryRequest;
import com.stashup.common.error.ApiException;
import com.stashup.common.error.ErrorCode;
import com.stashup.entry.EntryType;
import com.stashup.entry.FinancialEntryRepository;

@Service
public class CategoryService {

  private final CategoryRepository categories;
  private final FinancialEntryRepository entries;
  private final Clock clock;

  public CategoryService(
      CategoryRepository categories, FinancialEntryRepository entries, Clock clock) {
    this.categories = categories;
    this.entries = entries;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<CategoryResponse> list(UUID userId, @Nullable EntryType entryType) {
    List<Category> found = entryType == null
        ? categories.findVisibleTo(userId)
        : categories.findVisibleToByType(userId, entryType);
    return found.stream().map(CategoryResponse::from).toList();
  }

  @Transactional
  public CategoryResponse create(UUID userId, CreateCategoryRequest request) {
    if (categories.existsByUserIdAndEntryTypeAndName(
        userId, request.entryType(), request.name())) {
      throw new ApiException(ErrorCode.DUPLICATE_CATEGORY);
    }
    Category category =
        Category.userDefined(userId, request.entryType(), request.name(), clock.instant());
    return CategoryResponse.from(categories.save(category));
  }

  /**
   * Deleting a category that entries still reference would orphan them, so it is refused with
   * the count included — the user needs to know how much work reassignment is before deciding.
   */
  @Transactional
  public void delete(UUID userId, UUID categoryId) {
    Category category = categories
        .findByIdAndUserId(categoryId, userId)
        .orElseThrow(ApiException::notFound);

    long referencing = entries.countByUserIdAndCategoryId(userId, category.getId());
    if (referencing > 0) {
      throw new ApiException(
          ErrorCode.CATEGORY_IN_USE,
          "Category is used by " + referencing + " entries",
          List.of(),
          java.util.Map.of("entryCount", referencing));
    }
    categories.delete(category);
  }

  /** Resolves a category the caller may use, enforcing the type match at the same time. */
  @Transactional(readOnly = true)
  public Category requireUsable(UUID userId, UUID categoryId, EntryType entryType) {
    Category category = categories
        .findVisibleToById(userId, categoryId)
        .orElseThrow(() -> new ApiException(ErrorCode.UNKNOWN_CATEGORY));
    if (category.getEntryType() != entryType) {
      throw new ApiException(ErrorCode.CATEGORY_TYPE_MISMATCH);
    }
    return category;
  }
}
