package com.stashup.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stashup.domain.FriendshipStatus;
import com.stashup.entity.Friendship;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

  Optional<Friendship> findByUserAIdAndUserBId(UUID userAId, UUID userBId);

  @Query("""
      SELECT f FROM Friendship f
      WHERE (f.userAId = :userId OR f.userBId = :userId) AND f.status = :status
      ORDER BY f.createdAt DESC
      """)
  List<Friendship> findByUserAndStatus(
      @Param("userId") UUID userId, @Param("status") FriendshipStatus status);

  /** Everything touching this user, in any state — used to compute search visibility. */
  @Query("SELECT f FROM Friendship f WHERE f.userAId = :userId OR f.userBId = :userId")
  List<Friendship> findAllInvolving(@Param("userId") UUID userId);

  default Optional<Friendship> findBetween(UUID left, UUID right) {
    return findByUserAIdAndUserBId(Friendship.lower(left, right), Friendship.higher(left, right));
  }

  default List<Friendship> findAccepted(UUID userId) {
    return findByUserAndStatus(userId, FriendshipStatus.ACCEPTED);
  }
}
