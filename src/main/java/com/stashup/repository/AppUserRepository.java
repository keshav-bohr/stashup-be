package com.stashup.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stashup.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

  Optional<AppUser> findByEmail(String email);

  boolean existsByEmail(String email);

  /**
   * Prefix search on display name.
   *
   * <p>Returns identity only — the caller maps to a summary that carries no score or band. Users
   * who have blocked the caller are filtered out downstream by the friendship layer, which owns
   * that knowledge.
   */
  @Query(
      """
      SELECT u FROM AppUser u
      WHERE u.id <> :callerId
        AND LOWER(u.displayName) LIKE LOWER(CONCAT(:prefix, '%'))
      ORDER BY u.displayName ASC, u.id ASC
      """)
  List<AppUser> searchByDisplayNamePrefix(
      @Param("callerId") UUID callerId, @Param("prefix") String prefix, Limit limit);
}
