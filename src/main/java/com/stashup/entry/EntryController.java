package com.stashup.entry;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stashup.common.idempotency.IdempotencyService;
import com.stashup.common.page.KeysetPage;
import com.stashup.common.page.PageLimit;
import com.stashup.entry.EntryDtos.CreateEntryRequest;
import com.stashup.entry.EntryDtos.EntryResponse;
import com.stashup.entry.EntryDtos.UpdateEntryRequest;
import com.stashup.security.CurrentUserId;

@RestController
@RequestMapping("/api/v1/entries")
public class EntryController {

  private final EntryService entryService;
  private final IdempotencyService idempotencyService;
  private final PageLimit pageLimit;

  public EntryController(
      EntryService entryService, IdempotencyService idempotencyService, PageLimit pageLimit) {
    this.entryService = entryService;
    this.idempotencyService = idempotencyService;
    this.pageLimit = pageLimit;
  }

  /**
   * The idempotency key is required, not optional. FR-010 is a guarantee, and it cannot be one if
   * the client is allowed to omit the key.
   */
  @PostMapping
  public ResponseEntity<EntryResponse> create(
      @CurrentUserId UUID userId,
      @RequestHeader("Idempotency-Key") @Size(max = 64) String idempotencyKey,
      @Valid @RequestBody CreateEntryRequest request) {

    IdempotencyService.Replayable<EntryResponse> result = idempotencyService.execute(
        userId,
        idempotencyKey,
        request,
        EntryResponse.class,
        () -> entryService.create(userId, request));

    return ResponseEntity.status(HttpStatus.CREATED).body(result.value());
  }

  @GetMapping
  public KeysetPage<EntryResponse> list(
      @CurrentUserId UUID userId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate to,
      @RequestParam(required = false) @Nullable EntryType entryType,
      @RequestParam(required = false) @Nullable UUID categoryId,
      @RequestParam(required = false) @Nullable String cursor,
      @RequestParam(required = false) @Nullable Integer limit) {

    return entryService.list(
        userId, from, to, entryType, categoryId, cursor, pageLimit.validate(limit));
  }

  @GetMapping("/{entryId}")
  public EntryResponse get(@CurrentUserId UUID userId, @PathVariable UUID entryId) {
    return entryService.get(userId, entryId);
  }

  @PatchMapping("/{entryId}")
  public EntryResponse update(
      @CurrentUserId UUID userId,
      @PathVariable UUID entryId,
      @Valid @RequestBody UpdateEntryRequest request) {
    return entryService.update(userId, entryId, request);
  }

  @DeleteMapping("/{entryId}")
  public ResponseEntity<Void> delete(@CurrentUserId UUID userId, @PathVariable UUID entryId) {
    entryService.delete(userId, entryId);
    return ResponseEntity.noContent().build();
  }
}
