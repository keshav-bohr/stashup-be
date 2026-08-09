package com.stashup.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.stashup.util.UuidV7;

/**
 * A user's confirmation that a period's gap is explained by money held before the period.
 *
 * <p>Acknowledging changes no recorded amount and no score. It only moves the period out of the
 * unreconciled state so it becomes eligible for friend comparison again.
 */
@Entity
@Table(name = "drawdown_acknowledgment")
public class DrawdownAcknowledgment {

  @Id
  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "id", columnDefinition = "BINARY(16)")
  private UUID id;

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
  private UUID userId;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  /** The gap at the moment of acknowledgment — a bound, not an open licence. */
  @Column(name = "acknowledged_gap_minor", nullable = false)
  private long acknowledgedGapMinor;

  @Column(name = "acknowledged_at", nullable = false)
  private Instant acknowledgedAt;

  protected DrawdownAcknowledgment() {
    // for JPA
  }

  public static DrawdownAcknowledgment of(
      UUID userId, LocalDate periodStart, long gapMinor, Instant now) {
    DrawdownAcknowledgment acknowledgment = new DrawdownAcknowledgment();
    acknowledgment.id = UuidV7.generate();
    acknowledgment.userId = userId;
    acknowledgment.periodStart = periodStart;
    acknowledgment.acknowledgedGapMinor = gapMinor;
    acknowledgment.acknowledgedAt = now;
    return acknowledgment;
  }

  public long getAcknowledgedGapMinor() {
    return acknowledgedGapMinor;
  }

  public Instant getAcknowledgedAt() {
    return acknowledgedAt;
  }

  public LocalDate getPeriodStart() {
    return periodStart;
  }

  /** Re-acknowledging a widened gap updates the bound rather than creating a second row. */
  public void reacknowledge(long gapMinor, Instant now) {
    this.acknowledgedGapMinor = gapMinor;
    this.acknowledgedAt = now;
  }
}
