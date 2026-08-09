package com.stashup.web;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import com.stashup.config.ApplicationProperties;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

/**
 * Per-instance token buckets.
 *
 * <p>Accepted deviation, documented in plan.md: a globally accurate limiter needs shared state,
 * which is infrastructure ahead of need for a v1. Replacing this with a Bucket4j
 * {@code ProxyManager} over Redis requires no change at any call site.
 */
@Component
public class InMemoryRateLimiter implements RateLimiter {

  private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final ApplicationProperties.RateLimit config;

  public InMemoryRateLimiter(ApplicationProperties properties) {
    this.config = properties.rateLimit();
  }

  @Override
  public boolean tryConsume(String bucketKey, Tier tier) {
    Bucket bucket = buckets.computeIfAbsent(tier.name() + ':' + bucketKey, key -> newBucket(tier));
    return bucket.tryConsume(1);
  }

  private Bucket newBucket(Tier tier) {
    int capacity = tier == Tier.AUTH ? config.authCapacity() : config.writeCapacity();
    int refill = tier == Tier.AUTH ? config.authRefillPerMinute() : config.writeRefillPerMinute();
    return Bucket.builder()
        .addLimit(Bandwidth.builder()
            .capacity(capacity)
            .refillGreedy(refill, Duration.ofMinutes(1))
            .build())
        .build();
  }
}
