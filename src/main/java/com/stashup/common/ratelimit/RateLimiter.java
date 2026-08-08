package com.stashup.common.ratelimit;

/**
 * Rate limiting behind an interface so the in-memory implementation can be swapped for a
 * Redis-backed one without touching call sites.
 *
 * <p>See plan.md Complexity Tracking: the v1 implementation is per-instance, so the effective
 * cluster-wide limit is {@code N x configured}. Per-account login lockout is deliberately
 * <em>not</em> handled here — it lives in the database, where it is exact across instances,
 * because approximate brute-force protection is not protection.
 */
public interface RateLimiter {

  /**
   * @return true if the request may proceed
   */
  boolean tryConsume(String bucketKey, Tier tier);

  /** Which budget a request draws from. */
  enum Tier {
    /** Unauthenticated credential-handling endpoints. */
    AUTH,
    /** Authenticated state-changing endpoints. */
    WRITE
  }
}
