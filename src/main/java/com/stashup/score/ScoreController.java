package com.stashup.score;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Pattern;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stashup.common.error.ApiException;
import com.stashup.common.error.ErrorCode;
import com.stashup.period.PeriodRef;
import com.stashup.score.ScoreDtos.ScoreResponse;
import com.stashup.security.CurrentUserId;

@RestController
@RequestMapping("/api/v1/scores")
public class ScoreController {

  private final ScoreService scoreService;

  public ScoreController(ScoreService scoreService) {
    this.scoreService = scoreService;
  }

  /**
   * Always returns the score to its owner, whatever its completeness state. Completeness gates
   * eligibility for friend rankings, never the owner's view of their own number (FR-029).
   */
  @GetMapping("/{period}")
  public ScoreResponse score(@CurrentUserId UUID userId, @PathVariable String period) {
    return scoreService.score(userId, PeriodRef.parse(period));
  }

  @GetMapping
  public List<ScoreResponse> history(
      @CurrentUserId UUID userId,
      @RequestParam @Pattern(regexp = "^\\d{4}-\\d{2}$") String from,
      @RequestParam @Pattern(regexp = "^\\d{4}-\\d{2}$") String to) {

    YearMonth start = parseMonth(from);
    YearMonth end = parseMonth(to);
    if (start.isAfter(end)) {
      throw new ApiException(ErrorCode.INVALID_PERIOD, "from must not be after to");
    }
    return scoreService.history(userId, start, end);
  }

  private static YearMonth parseMonth(String raw) {
    try {
      return YearMonth.parse(raw);
    } catch (java.time.format.DateTimeParseException ex) {
      throw new ApiException(ErrorCode.INVALID_PERIOD);
    }
  }
}
