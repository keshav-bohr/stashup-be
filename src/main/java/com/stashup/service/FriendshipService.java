package com.stashup.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stashup.domain.FriendshipStatus;
import com.stashup.entity.AppUser;
import com.stashup.entity.Friendship;
import com.stashup.exception.ApiException;
import com.stashup.exception.ErrorCode;
import com.stashup.repository.AppUserRepository;
import com.stashup.repository.FriendshipRepository;

/**
 * The friendship state machine.
 *
 * <p>Two behaviours here look like bugs and are not:
 *
 * <ul>
 *   <li>Requesting someone who has blocked you <b>succeeds</b> and does nothing. Returning an
 *       error would make blocks detectable by probing, which defeats the point of a block.
 *   <li>Declining returns success and leaves no trace the requester can observe. FR-031 requires
 *       that a requester cannot distinguish "declined" from "not yet answered".
 * </ul>
 */
@Service
public class FriendshipService {

  private final FriendshipRepository friendships;
  private final AppUserRepository users;
  private final Clock clock;

  public FriendshipService(
      FriendshipRepository friendships, AppUserRepository users, Clock clock) {
    this.friendships = friendships;
    this.users = users;
    this.clock = clock;
  }

  @Transactional
  public Friendship request(UUID requesterId, UUID targetId) {
    if (requesterId.equals(targetId)) {
      throw new ApiException(ErrorCode.SELF_REQUEST);
    }
    AppUser target = users.findById(targetId).orElseThrow(ApiException::notFound);
    Instant now = clock.instant();

    Optional<Friendship> existing = friendships.findBetween(requesterId, target.getId());
    if (existing.isPresent()) {
      return handleExisting(existing.get(), requesterId, now);
    }
    try {
      return friendships.save(Friendship.requested(requesterId, target.getId(), now));
    } catch (DataIntegrityViolationException ex) {
      // The other user requested us at the same moment. Their row won the unique constraint;
      // ours is the acceptance of it.
      Friendship theirs = friendships
          .findBetween(requesterId, target.getId())
          .orElseThrow(() -> ex);
      theirs.accept(now);
      return theirs;
    }
  }

  private Friendship handleExisting(Friendship friendship, UUID requesterId, Instant now) {
    return switch (friendship.getStatus()) {
      case ACCEPTED -> throw new ApiException(ErrorCode.ALREADY_FRIENDS);
      // Silently a no-op: surfacing this would let a blocked user detect the block.
      case BLOCKED -> friendship;
      case PENDING -> {
        if (friendship.isRecipient(requesterId)) {
          // They asked first; asking back is an acceptance.
          friendship.accept(now);
        }
        yield friendship;
      }
    };
  }

  @Transactional
  public void accept(UUID userId, UUID requestId) {
    Friendship friendship = requirePending(userId, requestId);
    friendship.accept(clock.instant());
  }

  /** Deletes the request. The requester is never told this happened. */
  @Transactional
  public void decline(UUID userId, UUID requestId) {
    friendships.delete(requirePending(userId, requestId));
  }

  @Transactional
  public void removeFriend(UUID userId, UUID otherUserId) {
    Friendship friendship = friendships
        .findBetween(userId, otherUserId)
        .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
        .orElseThrow(ApiException::notFound);
    friendships.delete(friendship);
  }

  @Transactional
  public void block(UUID blockerId, UUID blockedId) {
    if (blockerId.equals(blockedId)) {
      throw new ApiException(ErrorCode.SELF_REQUEST);
    }
    users.findById(blockedId).orElseThrow(ApiException::notFound);
    Instant now = clock.instant();

    friendships
        .findBetween(blockerId, blockedId)
        .ifPresentOrElse(
            friendship -> friendship.block(blockerId, now),
            () -> friendships.save(Friendship.blocked(blockerId, blockedId, now)));
  }

  /** Unblocking restores no friendship — the pair returns to strangers. */
  @Transactional
  public void unblock(UUID blockerId, UUID blockedId) {
    Friendship friendship = friendships
        .findBetween(blockerId, blockedId)
        .filter(f -> f.getStatus() == FriendshipStatus.BLOCKED)
        .filter(f -> blockerId.equals(f.getBlockedByUserId()))
        .orElseThrow(ApiException::notFound);
    friendships.delete(friendship);
  }

  @Transactional(readOnly = true)
  public List<Friendship> pendingRequests(UUID userId, boolean incoming) {
    return friendships.findByUserAndStatus(userId, FriendshipStatus.PENDING).stream()
        .filter(f -> incoming
            ? !f.getInitiatedByUserId().equals(userId)
            : f.getInitiatedByUserId().equals(userId))
        .toList();
  }

  private Friendship requirePending(UUID userId, UUID requestId) {
    return friendships
        .findById(requestId)
        .filter(f -> f.getStatus() == FriendshipStatus.PENDING)
        .filter(f -> f.isRecipient(userId))
        .orElseThrow(ApiException::notFound);
  }
}
