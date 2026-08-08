package com.stashup.user;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stashup.common.error.ApiException;
import com.stashup.common.error.ErrorCode;
import com.stashup.security.RefreshTokenRepository;
import com.stashup.user.UserDtos.Profile;
import com.stashup.user.UserDtos.UpdateProfileRequest;

@Service
public class UserService {

  private final AppUserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final Clock clock;

  public UserService(
      AppUserRepository users, RefreshTokenRepository refreshTokens, Clock clock) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Profile getProfile(UUID userId) {
    return Profile.from(require(userId));
  }

  @Transactional
  public Profile updateProfile(UUID userId, UpdateProfileRequest request) {
    AppUser user = require(userId);
    Instant now = clock.instant();
    if (request.displayName() != null) {
      user.rename(request.displayName(), now);
    }
    if (request.timezone() != null) {
      validateTimezone(request.timezone());
      user.changeTimezone(request.timezone(), now);
    }
    return Profile.from(user);
  }

  /**
   * Hard delete. FR-003 requires the data to actually be removed, so soft deletion would be the
   * wrong default. Every owned table cascades from {@code app_user}; friendship rows referencing
   * this user are removed by the friendship layer's cascade so the account disappears from other
   * users' friend lists and comparison views.
   */
  @Transactional
  public void deleteAccount(UUID userId) {
    AppUser user = require(userId);
    refreshTokens.revokeAllForUser(userId, clock.instant());
    users.delete(user);
  }

  private AppUser require(UUID userId) {
    return users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
  }

  private static void validateTimezone(String timezone) {
    try {
      java.time.ZoneId.of(timezone);
    } catch (java.time.DateTimeException ex) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown timezone");
    }
  }
}
