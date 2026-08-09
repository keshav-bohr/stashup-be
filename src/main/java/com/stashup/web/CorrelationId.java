package com.stashup.web;

import org.slf4j.MDC;

/** Access to the current request's correlation ID, as bound by {@link CorrelationIdFilter}. */
public final class CorrelationId {

  public static final String MDC_KEY = "correlationId";
  public static final String HEADER = "X-Correlation-Id";

  private CorrelationId() {}

  /** Returns the current correlation ID, or {@code "none"} outside a request. */
  public static String current() {
    String value = MDC.get(MDC_KEY);
    return value == null ? "none" : value;
  }
}
