package com.stashup.period;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.stashup.category.CategoryDtos.CategoryResponse;
import com.stashup.entry.EntryDtos.MoneyDto;
import com.stashup.entry.EntryType;

public final class PeriodDtos {

  private PeriodDtos() {}

  public record TypeTotal(MoneyDto total, int entryCount) {}

  public record CategoryTotal(CategoryResponse category, MoneyDto total, int entryCount) {}

  /**
   * @param contributingMonths present for yearly summaries so the caller knows how much of the
   *     year the figures actually cover
   */
  public record PeriodSummaryResponse(
      String period,
      PeriodRef.Granularity granularity,
      String currency,
      Map<EntryType, TypeTotal> totalsByType,
      List<CategoryTotal> totalsByCategory,
      int entryCount,
      @Nullable Integer contributingMonths) {}
}
