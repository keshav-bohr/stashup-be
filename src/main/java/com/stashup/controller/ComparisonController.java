package com.stashup.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stashup.domain.PeriodRef;
import com.stashup.dto.ComparisonDtos.ComparisonResponse;
import com.stashup.security.CurrentUserId;
import com.stashup.service.ComparisonService;

@RestController
@RequestMapping("/api/v1/comparison")
public class ComparisonController {

  private final ComparisonService comparisonService;

  public ComparisonController(ComparisonService comparisonService) {
    this.comparisonService = comparisonService;
  }

  /**
   * Score, band, change, and streak — and nothing else — for the caller and their accepted
   * friends.
   *
   * <p>The response carries no amount, income, category, or reconciliation gap for anybody. See
   * {@link ComparisonDtos.ComparisonEntry}, which is a deliberately closed field set.
   */
  @GetMapping("/{period}")
  public ComparisonResponse compare(
      @CurrentUserId UUID userId, @PathVariable String period) {
    return comparisonService.compare(userId, PeriodRef.parse(period));
  }
}
