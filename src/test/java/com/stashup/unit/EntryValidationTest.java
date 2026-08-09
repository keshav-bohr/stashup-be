package com.stashup.unit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Currency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.stashup.domain.Direction;
import com.stashup.domain.EntryType;
import com.stashup.domain.Money;
import com.stashup.exception.ApiException;
import com.stashup.exception.ErrorCode;
import com.stashup.service.EntryValidator;

class EntryValidationTest {

  private static final Currency INR = Currency.getInstance("INR");
  private static final Instant NOW = Instant.parse("2026-08-09T06:00:00Z");
  private static final LocalDate TODAY_UTC = LocalDate.of(2026, 8, 9);

  private final EntryValidator validator =
      new EntryValidator(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void validEntryPasses() {
    assertThatCode(() -> validate(EntryType.EXPENSE, Direction.CONTRIBUTION, 1_200L, TODAY_UTC))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("zero and negative amounts are rejected with a specific code")
  void nonPositiveAmountRejected() {
    assertCode(
        () -> validate(EntryType.EXPENSE, Direction.CONTRIBUTION, 0L, TODAY_UTC),
        ErrorCode.AMOUNT_NOT_POSITIVE);
    assertCode(
        () -> validate(EntryType.EXPENSE, Direction.CONTRIBUTION, -1L, TODAY_UTC),
        ErrorCode.AMOUNT_NOT_POSITIVE);
  }

  @Test
  void futureDateRejected() {
    assertCode(
        () -> validate(EntryType.EXPENSE, Direction.CONTRIBUTION, 100L, TODAY_UTC.plusDays(1)),
        ErrorCode.DATE_IN_FUTURE);
  }

  @Test
  @DisplayName("today is judged in the user's timezone, not the server's")
  void todayIsEvaluatedInTheUsersZone() {
    // 06:00 UTC is already 2026-08-09 in Auckland (UTC+12) and still 2026-08-08 in Los Angeles.
    assertThatCode(() -> validator.validate(
            EntryType.EXPENSE, Direction.CONTRIBUTION, Money.of(100L, INR),
            LocalDate.of(2026, 8, 9), INR, "Pacific/Auckland"))
        .doesNotThrowAnyException();

    assertThatThrownBy(() -> validator.validate(
            EntryType.EXPENSE, Direction.CONTRIBUTION, Money.of(100L, INR),
            LocalDate.of(2026, 8, 9), INR, "America/Los_Angeles"))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void withdrawalOnlyValidForSavingsAndInvestments() {
    assertThatCode(() -> validate(EntryType.SAVING, Direction.WITHDRAWAL, 100L, TODAY_UTC))
        .doesNotThrowAnyException();
    assertThatCode(() -> validate(EntryType.INVESTMENT, Direction.WITHDRAWAL, 100L, TODAY_UTC))
        .doesNotThrowAnyException();

    assertCode(
        () -> validate(EntryType.INCOME, Direction.WITHDRAWAL, 100L, TODAY_UTC),
        ErrorCode.WITHDRAWAL_NOT_ALLOWED_FOR_TYPE);
    assertCode(
        () -> validate(EntryType.EXPENSE, Direction.WITHDRAWAL, 100L, TODAY_UTC),
        ErrorCode.WITHDRAWAL_NOT_ALLOWED_FOR_TYPE);
  }

  @Test
  void currencyMustMatchTheAccount() {
    assertThatThrownBy(() -> validator.validate(
            EntryType.EXPENSE, Direction.CONTRIBUTION,
            Money.of(100L, Currency.getInstance("USD")),
            TODAY_UTC, INR, "UTC"))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.CURRENCY_MISMATCH);
  }

  private void validate(EntryType type, Direction direction, long amount, LocalDate date) {
    validator.validate(type, direction, Money.of(amount, INR), date, INR, "UTC");
  }

  private static void assertCode(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(expected);
  }
}
