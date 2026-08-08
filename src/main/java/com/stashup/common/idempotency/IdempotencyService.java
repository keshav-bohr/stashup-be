package com.stashup.common.idempotency;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.stashup.common.error.ApiException;
import com.stashup.common.error.ErrorCode;

import tools.jackson.databind.ObjectMapper;

/**
 * Makes a retried write safe (FR-010).
 *
 * <p>Deduplication is by key only, never by content: two identical coffee purchases on the same
 * day are both legitimate and must both be recorded. The uniqueness guarantee is the database's
 * job via {@code uq_idempotency_user_key}, so it holds under concurrent retries rather than
 * depending on application-level check-then-act.
 *
 * <p>Reusing a key with a <em>different</em> body is a 409 rather than a silent replay of the
 * first response, because that combination always indicates a client bug and hiding it would make
 * the bug harder to find.
 *
 * <p>Note: this uses Jackson 3 ({@code tools.jackson}), which Spring Boot 4 ships. Its
 * serialization exceptions are unchecked, unlike Jackson 2's.
 */
@Service
public class IdempotencyService {

  private final IdempotencyRecordRepository records;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public IdempotencyService(
      IdempotencyRecordRepository records, ObjectMapper objectMapper, Clock clock) {
    this.records = records;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /**
   * Runs {@code action} at most once per {@code (userId, key)}.
   *
   * @param key the client-supplied idempotency key
   * @param request the request payload, hashed to detect key reuse with different content
   */
  public <T> Replayable<T> execute(
      UUID userId, String key, Object request, Class<T> responseType, Supplier<T> action) {

    String fingerprint = fingerprint(request);
    Optional<IdempotencyRecord> existing = records.findByUserIdAndIdempotencyKey(userId, key);

    if (existing.isPresent()) {
      IdempotencyRecord record = existing.get();
      if (!record.getRequestFingerprint().equals(fingerprint)) {
        throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
      }
      return new Replayable<>(objectMapper.readValue(record.getResponseBody(), responseType), true);
    }

    T response = action.get();
    Instant now = clock.instant();
    try {
      records.save(IdempotencyRecord.of(
          userId, key, fingerprint, 201, objectMapper.writeValueAsString(response), now));
    } catch (DataIntegrityViolationException ex) {
      // A concurrent retry won the race. Fingerprints matched, so the two results are equivalent
      // by construction; returning ours is correct.
      return new Replayable<>(response, false);
    }
    return new Replayable<>(response, false);
  }

  private String fingerprint(Object request) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(objectMapper.writeValueAsBytes(request)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required but unavailable", ex);
    }
  }

  /**
   * @param replayed true when this is a replay of a previously stored response
   */
  public record Replayable<T>(T value, boolean replayed) {}
}
