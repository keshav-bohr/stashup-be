package com.stashup.domain;

/** The state of the link between two users. */
public enum FriendshipStatus {
  /** Requested by one side, not yet answered. Confers no visibility. */
  PENDING,
  /** Mutually agreed. The only state in which scores are visible. */
  ACCEPTED,
  /** One side blocked the other. Mutual invisibility; no new requests. */
  BLOCKED
}
