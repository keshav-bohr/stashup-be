package com.stashup.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stashup.config.ApplicationProperties;
import com.stashup.repository.AppUserRepository;

/**
 * Per-account brute-force protection, counted in the database so it is exact across every
 * instance — unlike the per-instance request rate limiter, approximate credential protection is
 * not protection.
 *
 * <p>The counter is incremented in its <b>own</b> transaction. A failed login ends by throwing,
 * which rolls back the calling transaction; without {@code REQUIRES_NEW} the increment would be
 * rolled back with it and the account would never lock, no matter how many attempts were made.
 */
@Service
public class AccountLockoutService {

  private final AppUserRepository users;
  private final ApplicationProperties properties;
  private final Clock clock;

  public AccountLockoutService(
      AppUserRepository users, ApplicationProperties properties, Clock clock) {
    this.users = users;
    this.properties = properties;
    this.clock = clock;
  }

  /** Records a failed attempt and locks the account once the threshold is reached. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailure(UUID userId) {
    users.findById(userId).ifPresent(user -> user.recordFailedLogin(
        properties.security().maxFailedLogins(),
        properties.security().lockoutDuration(),
        clock.instant()));
  }
}
