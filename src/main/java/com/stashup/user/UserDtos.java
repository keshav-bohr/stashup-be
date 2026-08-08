package com.stashup.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/** Profile and search payloads. */
public final class UserDtos {

  private UserDtos() {}

  public record Profile(
      UUID id,
      String email,
      String displayName,
      String baseCurrency,
      String timezone,
      Instant createdAt) {

    public static Profile from(AppUser user) {
      return new Profile(
          user.getId(),
          user.getEmail(),
          user.getDisplayName(),
          user.getBaseCurrency().getCurrencyCode(),
          user.getTimezone(),
          user.getCreatedAt());
    }
  }

  /**
   * Base currency is deliberately absent: it is immutable after registration, so accepting it
   * here would imply a capability that does not exist.
   */
  public record UpdateProfileRequest(
      @Nullable @Size(min = 1, max = 50) String displayName,
      @Nullable @Size(max = 64) String timezone) {}

  /**
   * Identity only. This record carries no score and no band by construction — a non-friend must
   * learn nothing about another user's finances, and search is the easiest place to leak it.
   */
  public record UserSummary(UUID id, String displayName, String friendshipStatus) {}
}
