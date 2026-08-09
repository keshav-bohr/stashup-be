package com.stashup.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response payloads for the authentication endpoints. */
public final class AuthDtos {

  private AuthDtos() {}

  public record RegisterRequest(
      @Email @NotBlank @Size(max = 320) String email,
      @NotBlank @Size(min = 12, max = 128) String password,
      @NotBlank @Size(min = 1, max = 50) String displayName,
      @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO 4217 code") String baseCurrency,
      @NotBlank @Size(max = 64) String timezone) {}

  public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

  public record RefreshRequest(@NotBlank String refreshToken) {}

  public record TokenPair(String accessToken, String refreshToken, long expiresIn) {}
}
