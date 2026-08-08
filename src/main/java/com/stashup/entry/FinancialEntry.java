package com.stashup.entry;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import com.stashup.common.id.UuidV7;
import com.stashup.common.money.Money;

/**
 * A single recorded money movement.
 *
 * <p>{@code entryDate} is a {@link LocalDate}, not an instant. The date a user assigns to a
 * transaction is a fact about their calendar, so period membership is a plain date comparison
 * with no timezone conversion — which removes an entire class of month-boundary bug.
 */
@Entity
@Table(name = "financial_entry")
public class FinancialEntry {

  @Id
  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "id", columnDefinition = "BINARY(16)")
  private UUID id;

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type", nullable = false, length = 16)
  private EntryType entryType;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false, length = 12)
  private Direction direction;

  /** Always positive. {@link #direction} carries the sign. */
  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "entry_date", nullable = false)
  private LocalDate entryDate;

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "category_id", columnDefinition = "BINARY(16)", nullable = false)
  private UUID categoryId;

  @Column(name = "note", length = 500)
  private @Nullable String note;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected FinancialEntry() {
    // for JPA
  }

  public static FinancialEntry record(
      UUID userId,
      EntryType entryType,
      Direction direction,
      Money amount,
      LocalDate entryDate,
      UUID categoryId,
      @Nullable String note,
      Instant now) {
    FinancialEntry entry = new FinancialEntry();
    entry.id = UuidV7.generate();
    entry.userId = userId;
    entry.entryType = entryType;
    entry.direction = direction;
    entry.amountMinor = amount.amountMinor();
    entry.currency = amount.currency().getCurrencyCode();
    entry.entryDate = entryDate;
    entry.categoryId = categoryId;
    entry.note = note;
    entry.createdAt = now;
    entry.updatedAt = now;
    return entry;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public EntryType getEntryType() {
    return entryType;
  }

  public Direction getDirection() {
    return direction;
  }

  public Money getAmount() {
    return Money.of(amountMinor, Currency.getInstance(currency));
  }

  public LocalDate getEntryDate() {
    return entryDate;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public @Nullable String getNote() {
    return note;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void changeAmount(Money amount, Instant now) {
    this.amountMinor = amount.amountMinor();
    this.currency = amount.currency().getCurrencyCode();
    this.updatedAt = now;
  }

  public void changeDate(LocalDate newDate, Instant now) {
    this.entryDate = newDate;
    this.updatedAt = now;
  }

  public void changeCategory(UUID newCategoryId, Instant now) {
    this.categoryId = newCategoryId;
    this.updatedAt = now;
  }

  public void changeDirection(Direction newDirection, Instant now) {
    this.direction = newDirection;
    this.updatedAt = now;
  }

  public void changeNote(@Nullable String newNote, Instant now) {
    this.note = newNote;
    this.updatedAt = now;
  }
}
