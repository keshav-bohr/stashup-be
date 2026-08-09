package com.stashup.exception;

import java.util.List;
import java.util.Map;

/**
 * The single application exception type. Carries a stable {@link ErrorCode} so the HTTP status
 * and the client-facing code are decided at the throw site rather than inferred later.
 *
 * <p>The detail message is safe to return to clients. It must never contain a monetary amount,
 * a credential, or another user's data.
 */
public class ApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient ErrorCode code;
  private final transient List<FieldError> fieldErrors;
  private final transient Map<String, Object> extensions;

  public ApiException(ErrorCode code) {
    this(code, code.title(), List.of(), Map.of());
  }

  public ApiException(ErrorCode code, String detail) {
    this(code, detail, List.of(), Map.of());
  }

  public ApiException(
      ErrorCode code, String detail, List<FieldError> fieldErrors, Map<String, Object> extensions) {
    super(detail);
    this.code = code;
    this.fieldErrors = List.copyOf(fieldErrors);
    this.extensions = Map.copyOf(extensions);
  }

  public static ApiException notFound() {
    return new ApiException(ErrorCode.NOT_FOUND);
  }

  public ErrorCode code() {
    return code;
  }

  public List<FieldError> fieldErrors() {
    return fieldErrors;
  }

  public Map<String, Object> extensions() {
    return extensions;
  }

  /** A single field-level validation failure. */
  public record FieldError(String field, String message) {}
}
