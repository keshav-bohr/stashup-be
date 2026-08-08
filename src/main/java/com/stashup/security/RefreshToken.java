package com.stashup.security;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import com.stashup.common.id.UuidV7;

/**
 * A revocable session handle.
 *
 * <p>Stateless JWT access tokens cannot be revoked, but blocking, account deletion, and
 * credential compromise all need a live session invalidated. Pairing a short-lived access token
 * with a revocable refresh token bounds the exposure to the access-token window.
 *
 * <p>Only the SHA-256 hash is persisted. A database disclosure therefore does not yield usable
 * session tokens.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

  @Id
  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "id", columnDefinition = "BINARY(16)")
  private UUID id;

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private @Nullable Instant revokedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected RefreshToken() {
    // for JPA
  }

  public static RefreshToken issue(UUID userId, String tokenHash, Instant now, Instant expiresAt) {
    RefreshToken token = new RefreshToken();
    token.id = UuidV7.generate();
    token.userId = userId;
    token.tokenHash = tokenHash;
    token.createdAt = now;
    token.expiresAt = expiresAt;
    return token;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public @Nullable Instant getRevokedAt() {
    return revokedAt;
  }

  public boolean isUsableAt(Instant now) {
    return revokedAt == null && expiresAt.isAfter(now);
  }

  public void revoke(Instant now) {
    if (revokedAt == null) {
      this.revokedAt = now;
    }
  }
}
