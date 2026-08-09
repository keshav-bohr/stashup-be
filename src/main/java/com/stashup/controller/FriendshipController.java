package com.stashup.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stashup.dto.FriendshipDtos.FriendRequestResponse;
import com.stashup.dto.FriendshipDtos.UserIdRequest;
import com.stashup.dto.UserDtos.UserSummary;
import com.stashup.entity.AppUser;
import com.stashup.entity.Friendship;
import com.stashup.repository.AppUserRepository;
import com.stashup.security.CurrentUserId;
import com.stashup.service.FriendVisibilityService;
import com.stashup.service.FriendshipService;

@RestController
@RequestMapping("/api/v1")
public class FriendshipController {

  private final FriendshipService friendshipService;
  private final FriendVisibilityService visibility;
  private final AppUserRepository users;

  public FriendshipController(
      FriendshipService friendshipService,
      FriendVisibilityService visibility,
      AppUserRepository users) {
    this.friendshipService = friendshipService;
    this.visibility = visibility;
    this.users = users;
  }

  /**
   * Returns 201 even when the target has blocked the caller. Surfacing the block would make it
   * detectable by probing, which is exactly what a block is meant to prevent.
   */
  @PostMapping("/friend-requests")
  public ResponseEntity<FriendRequestResponse> request(
      @CurrentUserId UUID userId, @Valid @RequestBody UserIdRequest request) {
    Friendship friendship = friendshipService.request(userId, request.userId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(FriendRequestResponse.from(friendship));
  }

  @GetMapping("/friend-requests")
  public List<FriendRequestResponse> pending(
      @CurrentUserId UUID userId, @RequestParam String direction) {
    boolean incoming = "INCOMING".equalsIgnoreCase(direction);
    return friendshipService.pendingRequests(userId, incoming).stream()
        .map(FriendRequestResponse::from)
        .toList();
  }

  @PostMapping("/friend-requests/{requestId}/accept")
  public ResponseEntity<Void> accept(
      @CurrentUserId UUID userId, @PathVariable UUID requestId) {
    friendshipService.accept(userId, requestId);
    return ResponseEntity.noContent().build();
  }

  /** The requester is never informed that a decline occurred (FR-031). */
  @PostMapping("/friend-requests/{requestId}/decline")
  public ResponseEntity<Void> decline(
      @CurrentUserId UUID userId, @PathVariable UUID requestId) {
    friendshipService.decline(userId, requestId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/friends")
  public List<UserSummary> friends(@CurrentUserId UUID userId) {
    return visibility.acceptedFriendships(userId).stream()
        .map(friendship -> friendship.otherThan(userId))
        .map(users::findById)
        .flatMap(java.util.Optional::stream)
        .map(this::toSummary)
        .toList();
  }

  @DeleteMapping("/friends/{otherUserId}")
  public ResponseEntity<Void> remove(
      @CurrentUserId UUID userId, @PathVariable UUID otherUserId) {
    friendshipService.removeFriend(userId, otherUserId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/blocks")
  public ResponseEntity<Void> block(
      @CurrentUserId UUID userId, @Valid @RequestBody UserIdRequest request) {
    friendshipService.block(userId, request.userId());
    return ResponseEntity.noContent().build();
  }

  /** Unblocking restores no friendship; the pair return to being strangers. */
  @DeleteMapping("/blocks/{otherUserId}")
  public ResponseEntity<Void> unblock(
      @CurrentUserId UUID userId, @PathVariable UUID otherUserId) {
    friendshipService.unblock(userId, otherUserId);
    return ResponseEntity.noContent().build();
  }

  private UserSummary toSummary(AppUser user) {
    return new UserSummary(user.getId(), user.getDisplayName(), "ACCEPTED");
  }
}
