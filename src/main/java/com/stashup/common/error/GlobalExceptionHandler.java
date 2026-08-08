package com.stashup.common.error;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.stashup.common.correlation.CorrelationId;
import com.stashup.common.money.CurrencyMismatchException;

/**
 * The single error envelope for every failure path: RFC 9457 {@link ProblemDetail} extended with
 * a stable {@code code} and the request's {@code correlationId} (constitution Principle III).
 *
 * <p>Two deliberate choices here are security decisions, not conveniences:
 *
 * <ul>
 *   <li>An unexpected exception never leaks its message to the client. The correlation ID is the
 *       only handle the caller gets; the detail stays in the logs.
 *   <li>{@link AccessDeniedException} is rendered as 404, not 403. A distinct 403 would confirm
 *       that a given identifier belongs to a real record owned by someone else.
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final String BASE_TYPE = "https://stashup.com/problems/";

  @ExceptionHandler(ApiException.class)
  public ProblemDetail handleApi(ApiException ex) {
    ProblemDetail problem = base(ex.code(), ex.getMessage());
    if (!ex.fieldErrors().isEmpty()) {
      problem.setProperty("errors", ex.fieldErrors());
    }
    ex.extensions().forEach(problem::setProperty);
    return problem;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleBeanValidation(MethodArgumentNotValidException ex) {
    List<ApiException.FieldError> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(f -> new ApiException.FieldError(f.getField(), safeMessage(f.getDefaultMessage())))
            .toList();
    ProblemDetail problem = base(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.title());
    problem.setProperty("errors", errors);
    return problem;
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ProblemDetail handleParameterValidation(HandlerMethodValidationException ex) {
    LOG.debug("Parameter validation failed: {}", ex.getMessage());
    return base(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.title());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
    LOG.debug("Unreadable request body: {}", ex.getMessage());
    return base(ErrorCode.VALIDATION_FAILED, "Request body could not be parsed");
  }

  @ExceptionHandler(CurrencyMismatchException.class)
  public ProblemDetail handleCurrencyMismatch(CurrencyMismatchException ex) {
    return base(ErrorCode.CURRENCY_MISMATCH, ex.getMessage());
  }

  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleAuthentication(AuthenticationException ex) {
    LOG.debug("Authentication failed: {}", ex.getMessage());
    return base(ErrorCode.UNAUTHENTICATED, ErrorCode.UNAUTHENTICATED.title());
  }

  /**
   * Rendered as 404 rather than 403 on purpose: a 403 would confirm the resource exists.
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    LOG.debug("Access denied: {}", ex.getMessage());
    return base(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.title());
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    // The message may contain anything, including amounts. It goes to the log, never the client.
    LOG.error("Unhandled exception [correlationId={}]", CorrelationId.current(), ex);
    return base(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.title());
  }

  private ProblemDetail base(ErrorCode code, String detail) {
    HttpStatus status = code.status();
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setType(URI.create(BASE_TYPE + code.name().toLowerCase(java.util.Locale.ROOT)));
    problem.setTitle(code.title());
    problem.setDetail(detail);
    problem.setProperty("code", code.name());
    problem.setProperty("correlationId", CorrelationId.current());
    return problem;
  }

  private static String safeMessage(String message) {
    return message == null ? "Invalid value" : message;
  }
}
