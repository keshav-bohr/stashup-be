package com.stashup.period;

/**
 * Five equal bands over the 0–100 scale, identical for every user (FR-019).
 *
 * <p>The boundaries are fixed here rather than configured: a band that meant something different
 * for two friends would make the comparison view meaningless.
 */
public enum ScoreBand {
  BAND_0_19(0, 19),
  BAND_20_39(20, 39),
  BAND_40_59(40, 59),
  BAND_60_79(60, 79),
  BAND_80_100(80, 100);

  private final int lower;
  private final int upper;

  ScoreBand(int lower, int upper) {
    this.lower = lower;
    this.upper = upper;
  }

  public static ScoreBand forScore(int score) {
    if (score < 0 || score > 100) {
      throw new IllegalArgumentException("Score out of range: " + score);
    }
    for (ScoreBand band : values()) {
      if (score >= band.lower && score <= band.upper) {
        return band;
      }
    }
    throw new IllegalStateException("Unreachable: bands cover 0..100");
  }

  public int lower() {
    return lower;
  }

  public int upper() {
    return upper;
  }
}
