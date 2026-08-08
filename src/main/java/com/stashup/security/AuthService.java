package com.stashup.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stashup.common.config.ApplicationProperties;
import com.stashup.common.error.ApiException;
import com.stashup.common.error.ErrorCode;
import com.stashup.security.AuthDtos.LoginRequest;
import com.stashup.security.AuthDtos.RegisterRequest;
import com.stashup.security.AuthDtos.TokenPair;
import com.stashup.user.AppUser;
import com.stashup.user.AppUserRepository;

/** Registration, login, refresh rotation, and logout. */
@Service
public class AuthService {

  private final AppUserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final ApplicationProperties properties;
  private final Clock clock;

  public AuthService(
      AppUserRepository users,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      ApplicationProperties properties,
      Clock clock) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public TokenPair register(RegisterRequest request) {
    String email = request.email().toLowerCase(java.util.Locale.ROOT);
    if (users.existsByEmail(email)) {
      throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
    }
    Currency currency = parseCurrency(request.baseCurrency());
    validateTimezone(request.timezone());

    Instant now = clock.instant();
    AppUser user = AppUser.register(
        email,
        passwordEncoder.encode(request.password()),
        request.displayName(),
        currency,
        request.timezone(),
        now);
    users.save(user);
    return issueTokens(user, now);
  }

  /**
   * An unknown email and a wrong password produce an identical failure. Distinguishing them would
   * turn this endpoint into an account-enumeration oracle.
   */
  @Transactional
  public TokenPair login(LoginRequest request) {
    Instant now = clock.instant();
    Optional<AppUser> found = users.findByEmail(request.email().toLowerCase(java.util.Locale.ROOT));

    if (found.isEmpty()) {
      // Hash anyway so a missing account is not detectable by response timing.
      passwordEncoder.encode(request.password());
      throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
    }

    AppUser user = found.get();
    if (user.isLockedAt(now)) {
      throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
    }
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      user.recordFailedLogin(
          properties.security().maxFailedLogins(), properties.security().lockoutDuration(), now);
      throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
    }

    user.recordSuccessfulLogin(now);
    return issueTokens(user, now);
  }

  /** Rotates the refresh token: the presented one is revoked and a new one issued. */
  @Transactional
  public TokenPair refresh(String presentedToken) {
    Instant now = clock.instant();
    RefreshToken stored = refreshTokens
        .findByTokenHash(jwtService.hashRefreshToken(presentedToken))
        .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));

    if (!stored.isUsableAt(now)) {
      throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
    }
    AppUser user = users
        .findById(stored.getUserId())
        .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));

    stored.revoke(now);
    return issueTokens(user, now);
  }

  @Transactional
  public void logout(String presentedToken) {
    Instant now = clock.instant();
    refreshTokens
        .findByTokenHash(jwtService.hashRefreshToken(presentedToken))
        .ifPresent(token -> token.revoke(now));
  }

  private TokenPair issueTokens(AppUser user, Instant now) {
    String accessToken = jwtService.issueAccessToken(user.getId(), now);
    String refreshToken = jwtService.generateRefreshToken();
    refreshTokens.save(RefreshToken.issue(
        user.getId(),
        jwtService.hashRefreshToken(refreshToken),
        now,
        jwtService.refreshTokenExpiry(now)));
    return new TokenPair(accessToken, refreshToken, jwtService.accessTokenTtl().toSeconds());
  }

  private static Currency parseCurrency(String code) {
    try {
      return Currency.getInstance(code);
    } catch (IllegalArgumentException ex) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown currency code");
    }
  }

  private static void validateTimezone(String timezone) {
    try {
      java.time.ZoneId.of(timezone);
    } catch (java.time.DateTimeException ex) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown timezone");
    }
  }
}
