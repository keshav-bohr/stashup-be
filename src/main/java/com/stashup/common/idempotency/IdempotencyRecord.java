package com.stashup.common.idempotency;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import com.stashup.common.id.UuidV7;

/** A completed write, keyed so a retry replays rather than repeats it. */
@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

  @Id
  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "id", columnDefinition = "BINARY(16)")
  private UUID id;

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
  private UUID userId;

  @Column(name = "idempotency_key", nullable = false, length = 64)
  private String idempotencyKey;

  @Column(name = "request_fingerprint", nullable = false, length = 64)
  private String requestFingerprint;

  @Column(name = "response_status", nullable = false)
  private short responseStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_body")
  private @Nullable String responseBody;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected IdempotencyRecord() {
    // for JPA
  }

  public static IdempotencyRecord of(
      UUID userId,
      String idempotencyKey,
      String requestFingerprint,
      int responseStatus,
      @Nullable String responseBody,
      Instant now) {
    IdempotencyRecord record = new IdempotencyRecord();
    record.id = UuidV7.generate();
    record.userId = userId;
    record.idempotencyKey = idempotencyKey;
    record.requestFingerprint = requestFingerprint;
    record.responseStatus = (short) responseStatus;
    record.responseBody = responseBody;
    record.createdAt = now;
    return record;
  }

  public String getRequestFingerprint() {
    return requestFingerprint;
  }

  public short getResponseStatus() {
    return responseStatus;
  }

  public @Nullable String getResponseBody() {
    return responseBody;
  }
}
