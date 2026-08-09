package com.stashup.dto;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.stashup.domain.Completeness;
import com.stashup.dto.EntryDtos.MoneyDto;

public final class ReconciliationDtos {

  private ReconciliationDtos() {}

  /** The two — and only two — ways a flagged period can be resolved. */
  public enum Resolution {
    RECORD_MISSING_INCOME,
    ACKNOWLEDGE_DRAWDOWN
  }

  public record AcknowledgmentView(MoneyDto acknowledgedGap, Instant acknowledgedAt) {}

  /**
   * Shown only when a period is unreconciled.
   *
   * <p>The copy must not assert or imply that the user has been dishonest (FR-025). Wrongly
   * accusing an honest user who genuinely spent down savings is a worse outcome than letting one
   * user post an unverified score, and {@code ReconciliationCopyTest} enforces that.
   */
  public record Prompt(String message, List<Resolution> resolutions) {}

  public record ReconciliationResponse(
      String period,
      Completeness state,
      MoneyDto moneyIn,
      MoneyDto outflow,
      MoneyDto gap,
      MoneyDto tolerance,
      @Nullable AcknowledgmentView acknowledgment,
      @Nullable Prompt prompt) {}
}
