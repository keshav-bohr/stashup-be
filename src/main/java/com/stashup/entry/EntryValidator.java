package com.stashup.entry;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;

import org.springframework.stereotype.Component;

import com.stashup.common.error.ApiException;
import com.stashup.common.error.ErrorCode;
import com.stashup.common.money.Money;

/**
 * The rules an entry must satisfy before it reaches persistence (FR-005, FR-006).
 *
 * <p>Each failure carries its own {@link ErrorCode} rather than a generic validation error,
 * because the client needs to explain to the user which specific thing was wrong.
 */
@Component
public class EntryValidator {

  private final Clock clock;

  public EntryValidator(Clock clock) {
    this.clock = clock;
  }

  public void validate(
      EntryType entryType,
      Direction direction,
      Money amount,
      LocalDate entryDate,
      Currency accountCurrency,
      String userTimezone) {

    if (!amount.isPositive()) {
      throw new ApiException(ErrorCode.AMOUNT_NOT_POSITIVE);
    }
    if (!amount.currency().equals(accountCurrency)) {
      throw new ApiException(
          ErrorCode.CURRENCY_MISMATCH,
          "Entries must be recorded in " + accountCurrency.getCurrencyCode());
    }
    if (entryDate.isAfter(today(userTimezone))) {
      throw new ApiException(ErrorCode.DATE_IN_FUTURE);
    }
    if (direction == Direction.WITHDRAWAL && !entryType.supportsWithdrawal()) {
      throw new ApiException(ErrorCode.WITHDRAWAL_NOT_ALLOWED_FOR_TYPE);
    }
  }

  /**
   * "Not in the future" is judged in the user's own timezone, not the server's. Someone in
   * Auckland recording today's coffee should not be told the date is in the future because the
   * server is still on yesterday in UTC.
   */
  private LocalDate today(String userTimezone) {
    return LocalDate.ofInstant(clock.instant(), ZoneId.of(userTimezone));
  }
}
