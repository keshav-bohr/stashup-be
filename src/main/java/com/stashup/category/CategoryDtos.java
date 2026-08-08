package com.stashup.category;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.stashup.entry.EntryType;

public final class CategoryDtos {

  private CategoryDtos() {}

  public record CategoryResponse(UUID id, EntryType entryType, String name, boolean system) {

    public static CategoryResponse from(Category category) {
      return new CategoryResponse(
          category.getId(), category.getEntryType(), category.getName(), category.isSystem());
    }
  }

  public record CreateCategoryRequest(
      @NotNull EntryType entryType, @NotNull @Size(min = 1, max = 50) String name) {}
}
