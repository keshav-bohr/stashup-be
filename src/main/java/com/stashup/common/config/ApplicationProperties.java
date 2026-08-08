package com.stashup.common.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * All tunable configuration, validated at startup.
 *
 * <p>Constitution Principle: the service must fail fast at startup on missing or malformed
 * configuration rather than degrading at runtime. Binding these as validated records means a
 * missing secret or a nonsensical tolerance stops the application before it accepts traffic.
 */
@ConfigurationProperties(prefix = "stashup")
@Validated
public record ApplicationProperties(
    @NotNull @Valid Security security,
    @NotNull @Valid Pagination pagination,
    @NotNull @Valid Idempotency idempotency,
    @NotNull @Valid Reconciliation reconciliation,
    @NotNull @Valid Comparison comparison,
    @NotNull @Valid RateLimit rateLimit) {

  /**
   * @param jwtSecret HMAC key material; must come from the environment, never from source.
   */
  public record Security(
      @NotBlank @Size(min = 32, message = "jwt-secret must be at least 32 characters for HS256") String jwtSecret,
      @NotNull Duration accessTokenTtl,
      @NotNull Duration refreshTokenTtl,
      @Min(1) int maxFailedLogins,
      @NotNull Duration lockoutDuration) {}

  public record Pagination(@Min(1) int maxPageSize) {}

  public record Idempotency(@NotNull Duration retention) {}

  /**
   * A period is flagged when its gap exceeds {@code max(tolerancePercent% of money in,
   * absoluteFloorMinor)}. The percentage alone misbehaves at small incomes, so the floor
   * suppresses noise; both are tunable without a code change.
   */
  public record Reconciliation(
      @Min(0) int tolerancePercent, @Min(0) long absoluteFloorMinor) {}

  public record Comparison(@Min(1) int streakLookbackMonths) {}

  public record RateLimit(
      @Min(1) int writeCapacity,
      @Min(1) int writeRefillPerMinute,
      @Min(1) int authCapacity,
      @Min(1) int authRefillPerMinute) {}
}
