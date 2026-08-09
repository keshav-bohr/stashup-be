package com.stashup.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes.
 *
 * <p>Constitution Principle III: clients match on these, so a code MUST NOT be reworded or
 * repurposed once published. The human-readable title is free to improve; the enum constant is
 * the contract. Every value here appears in {@code contracts/openapi.yaml}.
 */
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request failed validation"),
  AMOUNT_NOT_POSITIVE(HttpStatus.BAD_REQUEST, "Amount must be greater than zero"),
  DATE_IN_FUTURE(HttpStatus.BAD_REQUEST, "Entry date cannot be in the future"),
  UNKNOWN_CATEGORY(HttpStatus.BAD_REQUEST, "Category does not exist"),
  CATEGORY_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "Category does not apply to this entry type"),
  WITHDRAWAL_NOT_ALLOWED_FOR_TYPE(
      HttpStatus.BAD_REQUEST, "Withdrawals are only valid for savings and investments"),
  CURRENCY_MISMATCH(HttpStatus.BAD_REQUEST, "Amount currency must match the account currency"),
  INVALID_PERIOD(HttpStatus.BAD_REQUEST, "Period must be YYYY-MM or YYYY"),
  PAGE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "Requested page size exceeds the maximum"),
  INVALID_CURSOR(HttpStatus.BAD_REQUEST, "Pagination cursor is malformed"),
  BASE_CURRENCY_IMMUTABLE(HttpStatus.BAD_REQUEST, "Base currency cannot be changed"),
  SELF_REQUEST(HttpStatus.BAD_REQUEST, "Cannot send a friend request to yourself"),
  ALREADY_FRIENDS(HttpStatus.BAD_REQUEST, "Already connected with this user"),

  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
  INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired"),

  NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),

  EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Email is already registered"),
  DUPLICATE_CATEGORY(HttpStatus.CONFLICT, "A category with this name already exists"),
  CATEGORY_IN_USE(HttpStatus.CONFLICT, "Category is still referenced by entries"),
  IDEMPOTENCY_KEY_REUSED(
      HttpStatus.CONFLICT, "Idempotency key was reused with a different request"),
  NO_GAP_TO_ACKNOWLEDGE(HttpStatus.CONFLICT, "This period has no gap to acknowledge"),

  ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account is temporarily locked"),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

  private final HttpStatus status;
  private final String title;

  ErrorCode(HttpStatus status, String title) {
    this.status = status;
    this.title = title;
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }
}
