package com.stashup.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stashup.domain.PeriodRef;
import com.stashup.dto.ReconciliationDtos.ReconciliationResponse;
import com.stashup.exception.ApiException;
import com.stashup.exception.ErrorCode;
import com.stashup.security.CurrentUserId;
import com.stashup.service.ReconciliationViewService;

@RestController
@RequestMapping("/api/v1/periods")
public class ReconciliationController {

  private final ReconciliationViewService viewService;

  public ReconciliationController(ReconciliationViewService viewService) {
    this.viewService = viewService;
  }

  @GetMapping("/{period}/reconciliation")
  public ReconciliationResponse reconciliation(
      @CurrentUserId UUID userId, @PathVariable String period) {
    return viewService.view(userId, requireMonth(period));
  }

  /**
   * Idempotent. Changes no recorded amount and no score — it moves the period to {@code COMPLETE}
   * so it becomes eligible for friend comparison.
   */
  @PutMapping("/{period}/drawdown-acknowledgment")
  public ReconciliationResponse acknowledge(
      @CurrentUserId UUID userId, @PathVariable String period) {
    return viewService.acknowledge(userId, requireMonth(period));
  }

  @DeleteMapping("/{period}/drawdown-acknowledgment")
  public ResponseEntity<Void> withdraw(
      @CurrentUserId UUID userId, @PathVariable String period) {
    viewService.withdraw(userId, requireMonth(period));
    return ResponseEntity.noContent().build();
  }

  /** Reconciliation is a monthly concept; a year has no single gap to explain. */
  private static PeriodRef requireMonth(String raw) {
    PeriodRef period = PeriodRef.parse(raw);
    if (!period.isMonth()) {
      throw new ApiException(ErrorCode.INVALID_PERIOD, "Reconciliation applies to a month");
    }
    return period;
  }
}
