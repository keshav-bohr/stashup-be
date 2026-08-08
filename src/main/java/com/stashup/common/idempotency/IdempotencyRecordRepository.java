package com.stashup.common.idempotency;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

  Optional<IdempotencyRecord> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

  @Modifying
  @Transactional
  @Query("DELETE FROM IdempotencyRecord r WHERE r.createdAt < :cutoff")
  int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
