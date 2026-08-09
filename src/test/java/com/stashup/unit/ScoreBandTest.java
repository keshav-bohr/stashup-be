package com.stashup.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.stashup.domain.ScoreBand;

/** Bands must be identical for every user, or the comparison view means nothing (FR-019). */
class ScoreBandTest {

  @ParameterizedTest(name = "score {0} falls in {1}")
  @CsvSource({
    "0, BAND_0_19", "19, BAND_0_19",
    "20, BAND_20_39", "39, BAND_20_39",
    "40, BAND_40_59", "59, BAND_40_59",
    "60, BAND_60_79", "79, BAND_60_79",
    "80, BAND_80_100", "100, BAND_80_100",
  })
  void boundariesAreExact(int score, ScoreBand expected) {
    assertThat(ScoreBand.forScore(score)).isEqualTo(expected);
  }

  @Test
  @DisplayName("every score in 0..100 maps to exactly one band, with no gaps")
  void bandsCoverTheWholeScaleWithoutGaps() {
    for (int score = 0; score <= 100; score++) {
      assertThat(ScoreBand.forScore(score)).isNotNull();
    }
    ScoreBand[] bands = ScoreBand.values();
    for (int i = 1; i < bands.length; i++) {
      assertThat(bands[i].lower())
          .as("band %s must start immediately after %s", bands[i], bands[i - 1])
          .isEqualTo(bands[i - 1].upper() + 1);
    }
  }

  @Test
  void outOfRangeScoresAreRejected() {
    assertThatThrownBy(() -> ScoreBand.forScore(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ScoreBand.forScore(101)).isInstanceOf(IllegalArgumentException.class);
  }
}
