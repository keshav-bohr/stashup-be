package com.stashup.service;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Turns money-in and amount-stashed into a 0–100 stash score.
 *
 * <p>The score is a <em>proportion</em>, not an amount (FR-014, FR-015). A user who takes in 100
 * and stashes 10 scores 10; a user who takes in 10 and stashes 5 scores 50. The lower earner
 * scores higher because they stashed a greater share — which is the whole point, and is what
 * makes the friend comparison a contest of discipline rather than of income.
 *
 * <p>All arithmetic is integer. Constitution Principle III bars floating point from anything
 * touching money, and a ratio of two exact amounts has no business being approximated.
 */
@Component
public class ScoreCalculator {

  /** Full scale in basis points: 10000 bp == 100%. */
  public static final int FULL_SCALE_BP = 10_000;

  /**
   * Proportion in basis points, or {@code null} when there is no income to divide by.
   *
   * <p>Basis points rather than a rounded 0–100 score so that two users at 30.4% and 30.6% —
   * both of whom display 30 — still order deterministically in a comparison view.
   */
  public @Nullable Integer proportionBasisPoints(long moneyInMinor, long stashedMinor) {
    if (moneyInMinor <= 0L) {
      return null;
    }
    long stashed = Math.max(0L, stashedMinor);
    if (stashed >= moneyInMinor) {
      // Stashing more than you earned means drawing on prior balances. Cap rather than overflow
      // the scale (FR-017); the period is separately flagged for reconciliation.
      return FULL_SCALE_BP;
    }
    long scaled = Math.multiplyExact(stashed, (long) FULL_SCALE_BP);
    long rounded = (scaled + moneyInMinor / 2L) / moneyInMinor;
    return (int) Math.min(FULL_SCALE_BP, rounded);
  }

  /** The client-facing 0–100 score, rounded half-up from basis points. */
  public short scoreFromBasisPoints(int proportionBp) {
    int score = (proportionBp + 50) / 100;
    return (short) Math.clamp(score, 0, 100);
  }

  /** True when the amount stashed met or exceeded money in, so the score was capped at 100. */
  public boolean isCapped(long moneyInMinor, long stashedMinor) {
    return moneyInMinor > 0L && stashedMinor >= moneyInMinor;
  }
}
