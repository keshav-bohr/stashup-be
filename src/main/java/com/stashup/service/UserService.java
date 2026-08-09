package com.stashup.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stashup.dto.UserDtos.Profile;
import com.stashup.dto.UserDtos.UpdateProfileRequest;
import com.stashup.dto.UserDtos.UserSummary;
import com.stashup.entity.AppUser;
import com.stashup.exception.ApiException;
import com.stashup.exception.ErrorCode;
import com.stashup.repository.AppUserRepository;
import com.stashup.repository.RefreshTokenRepository;

@Service
public class UserService {

  private final AppUserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final FriendVisibilityService visibility;
  private final Clock clock;

  public UserService(
      AppUserRepository users,
      RefreshTokenRepository refreshTokens,
      FriendVisibilityService visibility,
      Clock clock) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.visibility = visibility;
    this.clock = clock;
  }

  /**
   * Finds other users by display-name prefix.
   *
   * <p>Blocked pairs are filtered out in both directions, so a blocked user cannot detect the
   * block by noticing they can still see the blocker.
   */
  @Transactional(readOnly = true)
  public List<UserSummary> search(UUID callerId, String query, int limit) {
    Set<UUID> hidden = visibility.hiddenFrom(callerId);
    return users.searchByDisplayNamePrefix(callerId, query, Limit.of(limit + hidden.size()))
        .stream()
        .filter(user -> !hidden.contains(user.getId()))
        .limit(limit)
        .map(user -> new UserSummary(
            user.getId(),
            user.getDisplayName(),
            visibility.statusLabel(callerId, user.getId())))
        .toList();
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
