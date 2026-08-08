package com.stashup.common.money;

import java.util.Currency;

/** Thrown when arithmetic is attempted across two different currencies. */
public class CurrencyMismatchException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public CurrencyMismatchException(Currency expected, Currency actual) {
    super("Cannot combine amounts in " + expected.getCurrencyCode() + " and "
        + actual.getCurrencyCode());
  }
}
