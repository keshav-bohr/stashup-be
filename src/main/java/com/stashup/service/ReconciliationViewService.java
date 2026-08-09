package com.stashup.service;

import java.util.Currency;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stashup.domain.Completeness;
import com.stashup.domain.Money;
import com.stashup.domain.PeriodRef;
import com.stashup.dto.EntryDtos.MoneyDto;
import com.stashup.dto.ReconciliationDtos.AcknowledgmentView;
import com.stashup.dto.ReconciliationDtos.ReconciliationResponse;
import com.stashup.entity.DrawdownAcknowledgment;
import com.stashup.entity.PeriodSummary;
import com.stashup.exception.ApiException;
import com.stashup.exception.ErrorCode;
import com.stashup.repository.AppUserRepository;
import com.stashup.repository.PeriodSummaryRepository;

/** Assembles the reconciliation view and applies acknowledgments. */
@Service
public class ReconciliationViewService {

  private final PeriodSummaryRepository summaries;
  private final ReconciliationService reconciliation;
  private final PeriodSummaryRecomputeService recompute;
  private final AppUserRepository users;

  public ReconciliationViewService(
      PeriodSummaryRepository summaries,
      ReconciliationService reconciliation,
      PeriodSummaryRecomputeService recompute,
      AppUserRepository users) {
    this.summaries = summaries;
    this.reconciliation = reconciliation;
    this.recompute = recompute;
    this.users = users;
  }

  @Transactional(readOnly = true)
  public ReconciliationResponse view(UUID userId, PeriodRef period) {
    return toResponse(period, require(userId, period));
  }

  @Transactional
  public ReconciliationResponse acknowledge(UUID userId, PeriodRef period) {
    PeriodSummary summary = require(userId, period);
    long tolerance = reconciliation.toleranceFor(summary.getMoneyInMinor());
    if (summary.getGapMinor() <= tolerance) {
      throw new ApiException(ErrorCode.NO_GAP_TO_ACKNOWLEDGE);
    }
    reconciliation.acknowledge(userId, period.start(), summary.getGapMinor());
    recompute.recompute(
        userId, java.time.YearMonth.from(period.start()), currencyOf(userId));
    return toResponse(period, require(userId, period));
  }

  @Transactional
  public void withdraw(UUID userId, PeriodRef period) {
    require(userId, period);
    reconciliation.withdraw(userId, period.start());
    recompute.recompute(
        userId, java.time.YearMonth.from(period.start()), currencyOf(userId));
  }

  private ReconciliationResponse toResponse(PeriodRef period, PeriodSummary summary) {
    Currency currency = summary.getCurrency();
    Money gap = Money.of(summary.getGapMinor(), currency);
    Money tolerance =
        Money.of(reconciliation.toleranceFor(summary.getMoneyInMinor()), currency);

    @Nullable DrawdownAcknowledgment acknowledgment =
        reconciliation.find(summary.getUserId(), period.start());

    return new ReconciliationResponse(
        period.label(),
        summary.getCompleteness(),
        MoneyDto.from(summary.moneyIn()),
        MoneyDto.from(summary.outflow()),
        MoneyDto.from(gap),
        MoneyDto.from(tolerance),
        acknowledgment == null
            ? null
            : new AcknowledgmentView(
                MoneyDto.from(Money.of(acknowledgment.getAcknowledgedGapMinor(), currency)),
                acknowledgment.getAcknowledgedAt()),
        summary.getCompleteness() == Completeness.UNRECONCILED
            ? ReconciliationPrompts.forGap(gap)
            : null);
  }

  private PeriodSummary require(UUID userId, PeriodRef period) {
    return summaries
        .findByUserIdAndPeriodStart(userId, period.start())
        .orElseThrow(ApiException::notFound);
  }

  private Currency currencyOf(UUID userId) {
    return users.findById(userId).orElseThrow(ApiException::notFound).getBaseCurrency();
  }
}
