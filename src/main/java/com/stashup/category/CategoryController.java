package com.stashup.category;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stashup.category.CategoryDtos.CategoryResponse;
import com.stashup.category.CategoryDtos.CreateCategoryRequest;
import com.stashup.entry.EntryType;
import com.stashup.security.CurrentUserId;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

  private final CategoryService categoryService;

  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  @GetMapping
  public List<CategoryResponse> list(
      @CurrentUserId UUID userId,
      @RequestParam(name = "entryType", required = false) @Nullable EntryType entryType) {
    return categoryService.list(userId, entryType);
  }

  @PostMapping
  public ResponseEntity<CategoryResponse> create(
      @CurrentUserId UUID userId, @Valid @RequestBody CreateCategoryRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(userId, request));
  }

  @DeleteMapping("/{categoryId}")
  public ResponseEntity<Void> delete(
      @CurrentUserId UUID userId, @PathVariable UUID categoryId) {
    categoryService.delete(userId, categoryId);
    return ResponseEntity.noContent().build();
  }
}
