package com.stashup.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.stashup.dto.AuthDtos.LoginRequest;
import com.stashup.dto.AuthDtos.RegisterRequest;
import com.stashup.dto.AuthDtos.TokenPair;
import com.stashup.dto.UserDtos.Profile;
import com.stashup.dto.UserDtos.UpdateProfileRequest;
import com.stashup.exception.ApiException;
import com.stashup.exception.ErrorCode;
import com.stashup.service.AuthService;
import com.stashup.service.JwtService;
import com.stashup.service.UserService;
import com.stashup.support.MySqlTestBase;

/** Registration, login, lockout, refresh rotation, logout, and profile management. */
class AuthFlowIT extends MySqlTestBase {

  private static final String PASSWORD = "correct-horse-battery-staple";

  @Autowired private AuthService authService;
  @Autowired private UserService userService;
  @Autowired private JwtService jwtService;

  @Test
  @DisplayName("registration returns a usable token pair")
  void registrationIssuesTokens() {
    TokenPair tokens = register();

    assertThat(tokens.accessToken()).isNotBlank();
    assertThat(tokens.refreshToken()).isNotBlank();
    assertThat(tokens.expiresIn()).isEqualTo(900L);
  }

  @Test
  @DisplayName("an email can only be registered once")
  void duplicateEmailRejected() {
    String email = uniqueEmail();
    authService.register(request(email));

    assertThatThrownBy(() -> authService.register(request(email)))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED);
  }

  @Test
  @DisplayName("an unknown currency or timezone is rejected at registration")
  void invalidCurrencyAndTimezoneRejected() {
    assertThatThrownBy(() -> authService.register(new RegisterRequest(
        uniqueEmail(), PASSWORD, "Nobody", "XYZ", "Asia/Kolkata")))
        .isInstanceOf(ApiException.class);

    assertThatThrownBy(() -> authService.register(new RegisterRequest(
        uniqueEmail(), PASSWORD, "Nobody", "INR", "Mars/Olympus_Mons")))
        .isInstanceOf(ApiException.class);
  }

  @Test
  @DisplayName("login succeeds with the right password")
  void loginSucceeds() {
    String email = uniqueEmail();
    authService.register(request(email));

    TokenPair tokens = authService.login(new LoginRequest(email, PASSWORD));

    assertThat(tokens.accessToken()).isNotBlank();
  }

  @Test
  @DisplayName("an unknown email and a wrong password fail identically")
  void credentialFailuresAreIndistinguishable() {
    String email = uniqueEmail();
    authService.register(request(email));

    ErrorCode wrongPassword = codeOf(() ->
        authService.login(new LoginRequest(email, "not-the-right-password")));
    ErrorCode unknownEmail = codeOf(() ->
        authService.login(new LoginRequest(uniqueEmail(), PASSWORD)));

    assertThat(wrongPassword)
        .as("distinguishing these would turn login into an account-enumeration oracle")
        .isEqualTo(unknownEmail)
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("repeated failures lock the account, and lockout is shared state")
  void repeatedFailuresLockTheAccount() {
    String email = uniqueEmail();
    authService.register(request(email));

    for (int attempt = 0; attempt < 5; attempt++) {
      assertThat(codeOf(() -> authService.login(new LoginRequest(email, "wrong"))))
          .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    assertThat(codeOf(() -> authService.login(new LoginRequest(email, PASSWORD))))
        .as("lockout counts live in the database, so they hold across instances")
        .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
  }

  @Test
  @DisplayName("refreshing rotates the token and invalidates the old one")
  void refreshRotatesTheToken() {
    TokenPair original = register();

    TokenPair rotated = authService.refresh(original.refreshToken());

    assertThat(rotated.refreshToken()).isNotEqualTo(original.refreshToken());
    assertThat(codeOf(() -> authService.refresh(original.refreshToken())))
        .as("a rotated-away refresh token must not be reusable")
        .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
  }

  @Test
  @DisplayName("logout revokes the refresh token")
  void logoutRevokes() {
    TokenPair tokens = register();

    authService.logout(tokens.refreshToken());

    assertThat(codeOf(() -> authService.refresh(tokens.refreshToken())))
        .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
  }

  @Test
  @DisplayName("an unrecognised refresh token is rejected")
  void unknownRefreshTokenRejected() {
    assertThat(codeOf(() -> authService.refresh("not-a-real-token")))
        .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
  }

  @Test
  @DisplayName("only the hash of a refresh token is ever stored")
  void refreshTokensAreHashed() {
    String token = jwtService.generateRefreshToken();

    String hash = jwtService.hashRefreshToken(token);

    assertThat(hash).hasSize(64).isNotEqualTo(token);
    assertThat(jwtService.hashRefreshToken(token)).isEqualTo(hash);
  }

  @Test
  @DisplayName("profile can be read and updated, but base currency cannot change")
  void profileLifecycle() {
    String email = uniqueEmail();
    TokenPair tokens = authService.register(request(email));
    UUID userId = subjectOf(tokens);

    Profile profile = userService.getProfile(userId);
    assertThat(profile.email()).isEqualTo(email);
    assertThat(profile.baseCurrency()).isEqualTo("INR");

    Profile updated = userService.updateProfile(
        userId, new UpdateProfileRequest("Renamed", "Europe/London"));

    assertThat(updated.displayName()).isEqualTo("Renamed");
    assertThat(updated.timezone()).isEqualTo("Europe/London");
    assertThat(updated.baseCurrency())
        .as("immutable: changing it would reinterpret every stored amount")
        .isEqualTo("INR");
  }

  @Test
  @DisplayName("an unknown timezone is rejected on update")
  void profileUpdateRejectsBadTimezone() {
    UUID userId = subjectOf(register());

    assertThatThrownBy(() -> userService.updateProfile(
        userId, new UpdateProfileRequest(null, "Nowhere/Land")))
        .isInstanceOf(ApiException.class);
  }

  @Test
  @DisplayName("deleting the account makes the profile unreachable")
  void deletedProfileIsGone() {
    UUID userId = subjectOf(register());

    userService.deleteAccount(userId);

    assertThat(codeOf(() -> userService.getProfile(userId))).isEqualTo(ErrorCode.NOT_FOUND);
  }

  private TokenPair register() {
    return authService.register(request(uniqueEmail()));
  }

  private static RegisterRequest request(String email) {
    return new RegisterRequest(email, PASSWORD, "Test User", "INR", "Asia/Kolkata");
  }

  private static String uniqueEmail() {
    return "auth-" + UUID.randomUUID() + "@example.com";
  }

  /** Reads the user id out of the access token, which is where controllers get it from. */
  private static UUID subjectOf(TokenPair tokens) {
    String payload = tokens.accessToken().split("\\.")[1];
    String json = new String(
        java.util.Base64.getUrlDecoder().decode(payload),
        java.nio.charset.StandardCharsets.UTF_8);
    int start = json.indexOf("\"sub\":\"") + 7;
    return UUID.fromString(json.substring(start, start + 36));
  }

  private static ErrorCode codeOf(Runnable action) {
    try {
      action.run();
      throw new AssertionError("Expected an ApiException");
    } catch (ApiException ex) {
      return ex.code();
    }
  }
}
