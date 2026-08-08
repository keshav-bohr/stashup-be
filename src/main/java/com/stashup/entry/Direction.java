package com.stashup.entry;

/**
 * Which way money moved for savings and investments.
 *
 * <p>Amounts are always stored positive; this carries the sign. A signed amount column would
 * invite a missing {@code abs()} somewhere to silently turn a deposit into a withdrawal.
 */
public enum Direction {
  CONTRIBUTION,
  WITHDRAWAL;

  public long signum() {
    return this == CONTRIBUTION ? 1L : -1L;
  }
}
