package com.stashup.period;

import java.util.ArrayList;
import java.util.Currency;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stashup.category.CategoryDtos.CategoryResponse;
import com.stashup.common.error.ApiException;
import com.stashup.common.money.Money;
import com.stashup.entry.EntryDtos.MoneyDto;
import com.stashup.entry.EntryService;
import com.stashup.entry.EntryType;
import com.stashup.entry.FinancialEntryRepository;
import com.stashup.period.PeriodDtos.CategoryTotal;
import com.stashup.period.PeriodDtos.PeriodSummaryResponse;
import com.stashup.period.PeriodDtos.TypeTotal;
import com.stashup.user.AppUserRepository;

/**
 * Reads summaries for a month or a year.
 *
 * <p>Type totals come from the materialised {@code period_summary} rows. The category breakdown
 * is computed on demand — it is only ever read by the single owning user for a single period, so
 * an indexed {@code GROUP BY} is cheaper than maintaining a second materialised table.
 */
@Service
public class PeriodSummaryService {

  private final PeriodSummaryRepository summaries;
  private final FinancialEntryRepository entries;
  private final AppUserRepository users;
  private final EntryService entryService;

  public PeriodSummaryService(
      PeriodSummaryRepository summaries,
      FinancialEntryRepository entries,
      AppUserRepository users,
      EntryService entryService) {
    this.summaries = summaries;
    this.entries = entries;
    this.users = users;
    this.entryService = entryService;
  }

  @Transactional(readOnly = true)
  public PeriodSummaryResponse summarise(UUID userId, PeriodRef period) {
    Currency currency = users
        .findById(userId)
        .orElseThrow(ApiException::notFound)
        .getBaseCurrency();

    List<PeriodSummary> rows = summaries.findByUserIdAndPeriodStartBetweenOrderByPeriodStartAsc(
        userId, period.start(), period.endInclusive());
    if (rows.isEmpty()) {
      throw ApiException.notFound();
    }

    Map<EntryType, TypeTotal> byType = foldTypeTotals(rows, currency);
    List<CategoryTotal> byCategory = categoryTotals(userId, period, currency);
    int entryCount = rows.stream().mapToInt(PeriodSummary::getEntryCount).sum();

    return new PeriodSummaryResponse(
        period.label(),
        period.granularity(),
        currency.getCurrencyCode(),
        byType,
        byCategory,
        entryCount,
        period.isMonth() ? null : rows.size());
  }

  /**
   * Sums the underlying figures across the period's months.
   *
   * <p>For a year this is the only correct approach: the totals are additive, so summing them is
   * exact, whereas any per-month derived value would need re-deriving from the sums rather than
   * averaging.
   */
  private static Map<EntryType, TypeTotal> foldTypeTotals(
      List<PeriodSummary> rows, Currency currency) {

    Map<EntryType, Long> sums = new EnumMap<>(EntryType.class);
    for (PeriodSummary row : rows) {
      sums.merge(EntryType.INCOME, row.moneyIn().amountMinor(), Long::sum);
      sums.merge(EntryType.EXPENSE, row.expense().amountMinor(), Long::sum);
      sums.merge(EntryType.SAVING, row.savingNet().amountMinor(), Long::sum);
      sums.merge(EntryType.INVESTMENT, row.investmentNet().amountMinor(), Long::sum);
      sums.merge(EntryType.DEDUCTION, row.deduction().amountMinor(), Long::sum);
    }

    Map<EntryType, TypeTotal> totals = new EnumMap<>(EntryType.class);
    sums.forEach((type, minor) ->
        totals.put(type, new TypeTotal(MoneyDto.from(Money.of(minor, currency)), 0)));
    return totals;
  }

  private List<CategoryTotal> categoryTotals(UUID userId, PeriodRef period, Currency currency) {
    Function<UUID, CategoryResponse> resolver = entryService.categoryResolver(userId);
    List<Object[]> rows =
        entries.aggregateByCategory(userId, period.start(), period.endInclusive());

    List<CategoryTotal> totals = new ArrayList<>(rows.size());
    for (Object[] row : rows) {
      UUID categoryId = (UUID) row[0];
      long minor = ((Number) row[1]).longValue();
      int count = ((Number) row[2]).intValue();
      CategoryResponse category = resolver.apply(categoryId);
      if (category != null) {
        totals.add(new CategoryTotal(
            category, MoneyDto.from(Money.of(minor, currency)), count));
      }
    }
    totals.sort((left, right) ->
        Long.compare(right.total().amountMinor(), left.total().amountMinor()));
    return totals;
  }
}
