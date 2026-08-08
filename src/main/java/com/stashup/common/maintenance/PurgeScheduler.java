package com.stashup.common.maintenance;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stashup.common.config.ApplicationProperties;
import com.stashup.common.idempotency.IdempotencyRecordRepository;
import com.stashup.security.RefreshTokenRepository;

/**
 * Removes expired refresh tokens and idempotency records past their retention window.
 *
 * <p>Both tables grow with traffic and neither has any value once expired. Left unpurged they
 * would eventually dominate the database and slow the indexed lookups that authentication and
 * write deduplication depend on.
 */
@Component
public class PurgeScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(PurgeScheduler.class);

  private final RefreshTokenRepository refreshTokens;
  private final IdempotencyRecordRepository idempotencyRecords;
  private final Duration idempotencyRetention;
  private final Clock clock;

  public PurgeScheduler(
      RefreshTokenRepository refreshTokens,
      IdempotencyRecordRepository idempotencyRecords,
      ApplicationProperties properties,
      Clock clock) {
    this.refreshTokens = refreshTokens;
    this.idempotencyRecords = idempotencyRecords;
    this.idempotencyRetention = properties.idempotency().retention();
    this.clock = clock;
  }

  @Scheduled(cron = "0 15 3 * * *")
  public void purge() {
    int tokens = refreshTokens.deleteExpiredBefore(clock.instant());
    int records = idempotencyRecords.deleteOlderThan(clock.instant().minus(idempotencyRetention));
    LOG.info("Purge complete: {} expired refresh tokens, {} idempotency records", tokens, records);
  }
}
