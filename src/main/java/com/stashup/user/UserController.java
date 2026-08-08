package com.stashup.user;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stashup.security.CurrentUserId;
import com.stashup.user.UserDtos.Profile;
import com.stashup.user.UserDtos.UpdateProfileRequest;

@RestController
@RequestMapping("/api/v1")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
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
