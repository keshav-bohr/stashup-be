package com.stashup.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stashup.dto.UserDtos.Profile;
import com.stashup.dto.UserDtos.UpdateProfileRequest;
import com.stashup.dto.UserDtos.UserSummary;
import com.stashup.security.CurrentUserId;
import com.stashup.service.UserService;
import com.stashup.web.PageLimit;

@RestController
@RequestMapping("/api/v1")
public class UserController {

  private final UserService userService;
  private final PageLimit pageLimit;

  public UserController(UserService userService, PageLimit pageLimit) {
    this.userService = userService;
    this.pageLimit = pageLimit;
  }

  /**
   * Search returns identity only — display name and friendship status.
   *
   * <p>No score and no band, regardless of whether the two users are friends. Search is the
   * easiest place to leak a score to a stranger, so the response type
   * ({@link UserSummary}) has no field capable of carrying one.
   */
  @GetMapping("/users")
  public List<UserSummary> search(
      @CurrentUserId UUID userId,
      @RequestParam @Size(min = 2, max = 50) String query,
      @RequestParam(required = false) Integer limit) {
    return userService.search(userId, query, pageLimit.validate(limit));
  }

  @GetMapping("/me")
  public Profile me(@CurrentUserId UUID userId) {
    return userService.getProfile(userId);
  }

  @PatchMapping("/me")
  public Profile updateMe(
      @CurrentUserId UUID userId, @Valid @RequestBody UpdateProfileRequest request) {
    return userService.updateProfile(userId, request);
  }

  @DeleteMapping("/me")
  public ResponseEntity<Void> deleteMe(@CurrentUserId UUID userId) {
    userService.deleteAccount(userId);
    return ResponseEntity.noContent().build();
  }
}
