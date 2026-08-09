package com.stashup.domain;

import java.util.Currency;
import java.util.Objects;

/**
 * An exact monetary amount in integer minor units with an explicit currency.
 *
 * <p>Constitution Principle III requires money to be integer minor units plus an explicit
 * currency. No floating point appears anywhere in this type or its callers — Checkstyle forbids
 * {@code Float} and {@code Double} on members precisely so this cannot drift.
 *
 * <p>Currency is carried with the number rather than alongside it so that adding two amounts in
 * different currencies fails immediately instead of silently producing a wrong total.
 */
public record Money(long amountMinor, Currency currency) {

  public Money {
    Objects.requireNonNull(currency, "currency");
  }

  public static Money of(long amountMinor, Currency currency) {
    return new Money(amountMinor, currency);
  }

  public static Money of(long amountMinor, String currencyCode) {
    return new Money(amountMinor, Currency.getInstance(currencyCode));
  }

  public static Money zero(Currency currency) {
    return new Money(0L, currency);
  }

  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
  }

  public Money minus(Money other) {
    requireSameCurrency(other);
    return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
  }

  public Money negated() {
    return new Money(Math.negateExact(amountMinor), currency);
  }

  /** Floors the amount at zero. Used where a negative net must contribute nothing. */
  public Money atLeastZero() {
    return amountMinor < 0L ? zero(currency) : this;
  }

  public boolean isNegative() {
    return amountMinor < 0L;
  }

  public boolean isZero() {
    return amountMinor == 0L;
  }

  public boolean isPositive() {
    return amountMinor > 0L;
  }

  public boolean isGreaterThan(Money other) {
    requireSameCurrency(other);
    return amountMinor > other.amountMinor;
  }

  public boolean isGreaterThanOrEqualTo(Money other) {
    requireSameCurrency(other);
    return amountMinor >= other.amountMinor;
  }

  public static Money max(Money left, Money right) {
    left.requireSameCurrency(right);
    return left.amountMinor >= right.amountMinor ? left : right;
  }

  private void requireSameCurrency(Money other) {
    Objects.requireNonNull(other, "other");
    if (!currency.equals(other.currency)) {
      throw new CurrencyMismatchException(currency, other.currency);
    }
  }
}
