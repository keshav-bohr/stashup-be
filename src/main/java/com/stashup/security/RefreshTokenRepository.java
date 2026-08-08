package com.stashup.security;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /** Revokes every live session for a user — used on password change, block, and deletion. */
  @Modifying
  @Transactional
  @Query(
      """
      UPDATE RefreshToken t SET t.revokedAt = :now
      WHERE t.userId = :userId AND t.revokedAt IS NULL
      """)
  int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

  @Modifying
  @Transactional
  @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
  int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
