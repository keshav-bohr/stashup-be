package com.stashup.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import com.stashup.domain.FriendshipStatus;
import com.stashup.entity.Friendship;

public final class FriendshipDtos {

  private FriendshipDtos() {}

  public record UserIdRequest(@NotNull UUID userId) {}

  /** A pending request. Carries no score for either party. */
  public record FriendRequestResponse(
      UUID id, UUID fromUserId, UUID toUserId, FriendshipStatus status, Instant createdAt) {

    public static FriendRequestResponse from(Friendship friendship) {
      UUID from = friendship.getInitiatedByUserId();
      return new FriendRequestResponse(
          friendship.getId(),
          from,
          friendship.otherThan(from),
          friendship.getStatus(),
          friendship.getCreatedAt());
    }
  }
}
