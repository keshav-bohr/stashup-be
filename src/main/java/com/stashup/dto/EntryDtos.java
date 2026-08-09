package com.stashup.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.stashup.domain.Direction;
import com.stashup.domain.EntryType;
import com.stashup.domain.Money;
import com.stashup.dto.CategoryDtos.CategoryResponse;
import com.stashup.entity.FinancialEntry;

public final class EntryDtos {

  private EntryDtos() {}

  /**
   * Money on the wire is always integer minor units plus an explicit currency. There is no
   * decimal representation anywhere in the contract.
   */
  public record MoneyDto(
      @NotNull @Positive Long amountMinor,
      @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency) {

    public static MoneyDto from(Money money) {
      return new MoneyDto(money.amountMinor(), money.currency().getCurrencyCode());
    }

    public Money toMoney() {
      return Money.of(amountMinor, currency);
    }
  }

  public record CreateEntryRequest(
      @NotNull EntryType entryType,
      @Nullable Direction direction,
      @NotNull @Valid MoneyDto amount,
      @NotNull LocalDate entryDate,
      @NotNull UUID categoryId,
      @Nullable @Size(max = 500) String note) {

    public Direction directionOrDefault() {
      return direction == null ? Direction.CONTRIBUTION : direction;
    }
  }

  /** All fields optional; only those present are changed. */
  public record UpdateEntryRequest(
      @Nullable @Valid MoneyDto amount,
      @Nullable LocalDate entryDate,
      @Nullable UUID categoryId,
      @Nullable Direction direction,
      @Nullable @Size(max = 500) String note) {}

  public record EntryResponse(
      UUID id,
      EntryType entryType,
      Direction direction,
      MoneyDto amount,
      LocalDate entryDate,
      CategoryResponse category,
      @Nullable String note,
      Instant createdAt,
      Instant updatedAt) {

    public static EntryResponse of(FinancialEntry entry, CategoryResponse category) {
      return new EntryResponse(
          entry.getId(),
          entry.getEntryType(),
          entry.getDirection(),
          MoneyDto.from(entry.getAmount()),
          entry.getEntryDate(),
          category,
          entry.getNote(),
          entry.getCreatedAt(),
          entry.getUpdatedAt());
    }
  }
}
