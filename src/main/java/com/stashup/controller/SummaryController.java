package com.stashup.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stashup.domain.PeriodRef;
import com.stashup.dto.PeriodDtos.PeriodSummaryResponse;
import com.stashup.security.CurrentUserId;
import com.stashup.service.PeriodSummaryService;

@RestController
@RequestMapping("/api/v1/summaries")
public class SummaryController {

  private final PeriodSummaryService summaryService;

  public SummaryController(PeriodSummaryService summaryService) {
    this.summaryService = summaryService;
  }

  /** {@code period} is {@code YYYY-MM} or {@code YYYY}; anything else is a 400. */
  @GetMapping("/{period}")
  public PeriodSummaryResponse summary(
      @CurrentUserId UUID userId, @PathVariable String period) {
    return summaryService.summarise(userId, PeriodRef.parse(period));
  }
}
