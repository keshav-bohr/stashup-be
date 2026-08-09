package com.stashup.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import com.stashup.config.ApplicationProperties;
import com.stashup.domain.PeriodTotals;
import com.stashup.entity.DrawdownAcknowledgment;
import com.stashup.repository.DrawdownAcknowledgmentRepository;

/**
 * Decides whether a period's figures account for themselves.
 *
 * <p>This exists because every figure is self-reported, so a user can inflate their score simply
 * by not recording all the money that came in. The system holds both sides of the ledger, so it
 * can check a period against itself: recorded money in should roughly account for recorded
 * outflow.
 *
 * <p>In practice this spends most of its life helping honest users notice income they forgot to
 * log. Concealing understated income deliberately would require also understating expenses, which
 * destroys the expense tracking the user came for — so the check makes cheating costly rather than
 * impossible. Fully self-consistent falsification remains undetectable without verification, which
 * is out of scope.
 */
@Service
public class ReconciliationService {

  private final DrawdownAcknowledgmentRepository acknowledgments;
  private final int tolerancePercent;
  private final long absoluteFloorMinor;
  private final Clock clock;

  public ReconciliationService(
      DrawdownAcknowledgmentRepository acknowledgments,
      ApplicationProperties properties,
      Clock clock) {
    this.acknowledgments = acknowledgments;
    this.tolerancePercent = properties.reconciliation().tolerancePercent();
    this.absoluteFloorMinor = properties.reconciliation().absoluteFloorMinor();
    this.clock = clock;
  }

  /**
   * {@code max(tolerancePercent% of money in, absoluteFloor)}.
   *
   * <p>The percentage alone misbehaves at small incomes — someone with 5,000 of recorded income
   * would be flagged over a 500 discrepancy that is probably a forgotten cash gift. The floor
   * suppresses that noise.
   */
  public long toleranceFor(long moneyInMinor) {
    long percentage = Math.max(0L, moneyInMinor) / 100L * tolerancePercent;
    return Math.max(percentage, absoluteFloorMinor);
  }

  /**
   * Whether the gap is explained, either by being inside tolerance or by an acknowledgment that
   * still covers it.
   */
  public boolean isReconciled(UUID userId, LocalDate periodStart, PeriodTotals totals) {
    long gap = totals.gapMinor();
    long tolerance = toleranceFor(totals.moneyInMinor());
    if (gap <= tolerance) {
      return true;
    }
    return acknowledgments
        .findByUserIdAndPeriodStart(userId, periodStart)
        .filter(ack -> gap <= ack.getAcknowledgedGapMinor() + tolerance)
        .isPresent();
  }

  /**
   * Drops an acknowledgment once the gap it explained has closed, so it cannot lie dormant and
   * silently absorb a future gap.
   */
  public void discardAcknowledgmentIfGapClosed(
      UUID userId, LocalDate periodStart, PeriodTotals totals) {
    if (totals.gapMinor() <= toleranceFor(totals.moneyInMinor())) {
      acknowledgments.deleteByUserIdAndPeriodStart(userId, periodStart);
    }
  }

  /** Records, or widens, the user's confirmation that they drew on money held beforehand. */
  public DrawdownAcknowledgment acknowledge(UUID userId, LocalDate periodStart, long gapMinor) {
    Instant now = clock.instant();
    Optional<DrawdownAcknowledgment> existing =
        acknowledgments.findByUserIdAndPeriodStart(userId, periodStart);
    if (existing.isPresent()) {
      DrawdownAcknowledgment acknowledgment = existing.get();
      acknowledgment.reacknowledge(gapMinor, now);
      return acknowledgment;
    }
    return acknowledgments.save(
        DrawdownAcknowledgment.of(userId, periodStart, gapMinor, now));
  }

  public void withdraw(UUID userId, LocalDate periodStart) {
    acknowledgments.deleteByUserIdAndPeriodStart(userId, periodStart);
  }

  public @Nullable DrawdownAcknowledgment find(UUID userId, LocalDate periodStart) {
    return acknowledgments.findByUserIdAndPeriodStart(userId, periodStart).orElse(null);
  }
}
