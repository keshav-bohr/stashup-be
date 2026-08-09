package com.stashup.service;

import java.util.List;

import com.stashup.domain.Money;
import com.stashup.dto.ReconciliationDtos.Prompt;
import com.stashup.dto.ReconciliationDtos.Resolution;

/**
 * The user-facing copy for an unreconciled period.
 *
 * <p>Kept in one place, and deliberately narrow, because this is the requirement most likely to
 * drift during implementation. The message describes the <em>data</em> as incomplete, never the
 * user as dishonest: it states the arithmetic, then offers the two resolutions. Any wording that
 * implies suspicion fails {@code ReconciliationCopyTest}.
 */
public final class ReconciliationPrompts {

  private ReconciliationPrompts() {}

  public static Prompt forGap(Money gap) {
    String message = ("This month's spending, saving, and deductions add up to %s more than the "
        + "income recorded for it. That usually means some income has not been entered yet — "
        + "or that money set aside earlier was used. Either way it is easy to sort out.")
        .formatted(format(gap));
    return new Prompt(
        message,
        List.of(Resolution.RECORD_MISSING_INCOME, Resolution.ACKNOWLEDGE_DRAWDOWN));
  }

  private static String format(Money gap) {
    int fractionDigits = Math.max(0, gap.currency().getDefaultFractionDigits());
    long divisor = (long) Math.pow(10, fractionDigits);
    long major = gap.amountMinor() / divisor;
    long minor = Math.abs(gap.amountMinor() % divisor);
    String amount = fractionDigits == 0
        ? String.valueOf(major)
        : "%d.%0" + fractionDigits + "d";
    String rendered = fractionDigits == 0
        ? amount
        : amount.formatted(major, minor);
    return gap.currency().getCurrencyCode() + " " + rendered;
  }
}
