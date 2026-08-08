package com.stashup.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.stashup.common.config.ApplicationProperties;

/**
 * Issues short-lived access tokens and opaque refresh tokens.
 *
 * <p>Access tokens are stateless JWTs so no session state lives in the process (Principle V).
 * Refresh tokens are opaque random strings, never JWTs — they must be revocable, and revocation
 * requires a database lookup regardless, so there is nothing to gain from making them
 * self-describing.
 */
@Service
public class JwtService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int REFRESH_TOKEN_BYTES = 32;

  private final JwtEncoder encoder;
  private final Duration accessTokenTtl;
  private final Duration refreshTokenTtl;

  public JwtService(JwtEncoder encoder, ApplicationProperties properties) {
    this.encoder = encoder;
    this.accessTokenTtl = properties.security().accessTokenTtl();
    this.refreshTokenTtl = properties.security().refreshTokenTtl();
  }

  public String issueAccessToken(UUID userId, Instant now) {
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("stashup")
        .issuedAt(now)
        .expiresAt(now.plus(accessTokenTtl))
        .subject(userId.toString())
        .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  /** Generates a fresh opaque refresh token. The plaintext is returned to the caller only once. */
  public String generateRefreshToken() {
    byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** SHA-256 of a refresh token. Only this value is ever persisted. */
  public String hashRefreshToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required but unavailable", ex);
    }
  }

  public Duration accessTokenTtl() {
    return accessTokenTtl;
  }

  public Instant refreshTokenExpiry(Instant now) {
    return now.plus(refreshTokenTtl);
  }
}
