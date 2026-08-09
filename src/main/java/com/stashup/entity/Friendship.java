package com.stashup.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import com.stashup.domain.FriendshipStatus;
import com.stashup.util.UuidV7;

/**
 * The link between two users, stored once per pair with the identifiers canonically ordered.
 *
 * <p>Callers never construct the pair themselves — {@link #between} does the ordering, so an
 * unordered row cannot be inserted by mistake.
 */
@Entity
@Table(name = "friendship")
public class Friendship {

  @Id
  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "id", columnDefinition = "BINARY(16)")
  private UUID id;

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "user_a_id", columnDefinition = "BINARY(16)", nullable = false)
  private UUID userAId;

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "user_b_id", columnDefinition = "BINARY(16)", nullable = false)
  private UUID userBId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 12)
  private FriendshipStatus status;

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "initiated_by_user_id", columnDefinition = "BINARY(16)", nullable = false)
  private UUID initiatedByUserId;

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "blocked_by_user_id", columnDefinition = "BINARY(16)")
  private @Nullable UUID blockedByUserId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Friendship() {
    // for JPA
  }

  /** Canonical ordering: the lower identifier is always {@code user_a_id}. */
  public static UUID lower(UUID left, UUID right) {
    return left.compareTo(right) <= 0 ? left : right;
  }

  public static UUID higher(UUID left, UUID right) {
    return left.compareTo(right) <= 0 ? right : left;
  }

  public static Friendship requested(UUID requester, UUID recipient, Instant now) {
    Friendship friendship = new Friendship();
    friendship.id = UuidV7.generate();
    friendship.userAId = lower(requester, recipient);
    friendship.userBId = higher(requester, recipient);
    friendship.status = FriendshipStatus.PENDING;
    friendship.initiatedByUserId = requester;
    friendship.createdAt = now;
    friendship.updatedAt = now;
    return friendship;
  }

  public static Friendship blocked(UUID blocker, UUID blocked, Instant now) {
    Friendship friendship = new Friendship();
    friendship.id = UuidV7.generate();
    friendship.userAId = lower(blocker, blocked);
    friendship.userBId = higher(blocker, blocked);
    friendship.status = FriendshipStatus.BLOCKED;
    friendship.initiatedByUserId = blocker;
    friendship.blockedByUserId = blocker;
    friendship.createdAt = now;
    friendship.updatedAt = now;
    return friendship;
  }

  public UUID getId() {
    return id;
  }

  public FriendshipStatus getStatus() {
    return status;
  }

  public UUID getInitiatedByUserId() {
    return initiatedByUserId;
  }

  public @Nullable UUID getBlockedByUserId() {
    return blockedByUserId;
  }

  public UUID getUserAId() {
    return userAId;
  }

  public UUID getUserBId() {
    return userBId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  /** The party who is not {@code viewer}. */
  public UUID otherThan(UUID viewer) {
    return userAId.equals(viewer) ? userBId : userAId;
  }

  public boolean involves(UUID userId) {
    return userAId.equals(userId) || userBId.equals(userId);
  }

  public boolean isRecipient(UUID userId) {
    return involves(userId) && !initiatedByUserId.equals(userId);
  }

  public void accept(Instant now) {
    this.status = FriendshipStatus.ACCEPTED;
    this.blockedByUserId = null;
    this.updatedAt = now;
  }

  public void block(UUID blocker, Instant now) {
    this.status = FriendshipStatus.BLOCKED;
    this.blockedByUserId = blocker;
    this.updatedAt = now;
  }
}
