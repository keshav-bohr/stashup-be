package com.stashup.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stashup.domain.FriendshipStatus;
import com.stashup.entity.Friendship;
import com.stashup.repository.FriendshipRepository;

/**
 * The single gate through which one user's score may become visible to another.
 *
 * <p>Every cross-user read path must go through here. Concentrating the rule in one place is what
 * makes FR-036 auditable: there is exactly one method to review, and any new feature that wants
 * another user's score has to call it.
 *
 * <p>The rule itself is deliberately narrow — an {@code ACCEPTED} row and nothing else. Absence of
 * a row, a pending request, a block, and a removed friendship all produce the same answer: no.
 */
@Service
public class FriendVisibilityService {

  private final FriendshipRepository friendships;

  public FriendVisibilityService(FriendshipRepository friendships) {
    this.friendships = friendships;
  }

  /** Whether {@code viewer} may see {@code target}'s score and band. */
  @Transactional(readOnly = true)
  public boolean canSeeScoreOf(UUID viewer, UUID target) {
    if (viewer.equals(target)) {
      return true;
    }
    return friendships
        .findBetween(viewer, target)
        .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
        .isPresent();
  }

  /**
   * The participants of a comparison: the viewer plus their accepted friends, in a stable order
   * with the viewer first.
   */
  @Transactional(readOnly = true)
  public Set<UUID> comparisonParticipants(UUID viewer) {
    Set<UUID> participants = new LinkedHashSet<>();
    participants.add(viewer);
    for (Friendship friendship : friendships.findAccepted(viewer)) {
      participants.add(friendship.otherThan(viewer));
    }
    return participants;
  }

  /**
   * Users who must not appear in {@code viewer}'s search results.
   *
   * <p>A block hides each user from the other, in both directions — the blocked party must not be
   * able to detect the block by noticing they can still see the blocker.
   */
  @Transactional(readOnly = true)
  public Set<UUID> hiddenFrom(UUID viewer) {
    Set<UUID> hidden = new LinkedHashSet<>();
    for (Friendship friendship : friendships.findAllInvolving(viewer)) {
      if (friendship.getStatus() == FriendshipStatus.BLOCKED) {
        hidden.add(friendship.otherThan(viewer));
      }
    }
    return hidden;
  }

  /** The friendship state between two users, as reported to the client. */
  @Transactional(readOnly = true)
  public String statusLabel(UUID viewer, UUID target) {
    return friendships
        .findBetween(viewer, target)
        .map(f -> switch (f.getStatus()) {
          case ACCEPTED -> "ACCEPTED";
          case PENDING -> f.getInitiatedByUserId().equals(viewer)
              ? "PENDING_OUTGOING"
              : "PENDING_INCOMING";
          // A block is reported as no relationship at all, so it stays undetectable.
          case BLOCKED -> "NONE";
        })
        .orElse("NONE");
  }

  @Transactional(readOnly = true)
  public List<Friendship> acceptedFriendships(UUID userId) {
    return friendships.findAccepted(userId);
  }
}
