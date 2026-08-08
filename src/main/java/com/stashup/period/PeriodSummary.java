package com.stashup.period;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import com.stashup.common.id.UuidV7;
import com.stashup.common.money.Money;

/**
 * Materialised totals for one user over one month, plus the score derived from them.
 *
 * <p>Granularity is always a month. Yearly figures are summed from these rows on read.
 */
@Entity
@Table(name = "period_summary")
public class PeriodSummary {

  @Id
  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "id", columnDefinition = "BINARY(16)")
  private UUID id;

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
  private UUID userId;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "money_in_minor", nullable = false)
  private long moneyInMinor;

  @Column(name = "expense_minor", nullable = false)
  private long expenseMinor;

  @Column(name = "saving_net_minor", nullable = false)
  private long savingNetMinor;

  @Column(name = "investment_net_minor", nullable = false)
  private long investmentNetMinor;

  @Column(name = "deduction_minor", nullable = false)
  private long deductionMinor;

  @Column(name = "stashed_minor", nullable = false)
  private long stashedMinor;

  @Column(name = "outflow_minor", nullable = false)
  private long outflowMinor;

  @Column(name = "gap_minor", nullable = false)
  private long gapMinor;

  @Column(name = "proportion_bp")
  private @Nullable Integer proportionBp;

  @Column(name = "score")
  private @Nullable Short score;

  @Enumerated(EnumType.STRING)
  @Column(name = "band", length = 16)
  private @Nullable ScoreBand band;

  @Enumerated(EnumType.STRING)
  @Column(name = "completeness", nullable = false, length = 20)
  private Completeness completeness;

  @Column(name = "entry_count", nullable = false)
  private int entryCount;

  @Column(name = "computed_at", nullable = false)
  private Instant computedAt;

  protected PeriodSummary() {
    // for JPA
  }

  public static PeriodSummary empty(UUID userId, LocalDate periodStart, Currency currency) {
    PeriodSummary summary = new PeriodSummary();
    summary.id = UuidV7.generate();
    summary.userId = userId;
    summary.periodStart = periodStart;
    summary.currency = currency.getCurrencyCode();
    summary.completeness = Completeness.INSUFFICIENT_DATA;
    return summary;
  }

  /** Overwrites the aggregate figures. Derived score fields are applied separately. */
  public void applyTotals(PeriodTotals totals, Instant now) {
    this.moneyInMinor = totals.moneyInMinor();
    this.expenseMinor = totals.expenseMinor();
    this.savingNetMinor = totals.savingNetMinor();
    this.investmentNetMinor = totals.investmentNetMinor();
    this.deductionMinor = totals.deductionMinor();
    this.stashedMinor = totals.stashedMinor();
    this.outflowMinor = totals.outflowMinor();
    this.gapMinor = totals.gapMinor();
    this.entryCount = totals.entryCount();
    this.computedAt = now;
  }

  public void applyScore(
      @Nullable Integer newProportionBp, @Nullable Short newScore, @Nullable ScoreBand newBand) {
    this.proportionBp = newProportionBp;
    this.score = newScore;
    this.band = newBand;
  }

  public void applyCompleteness(Completeness newCompleteness) {
    this.completeness = newCompleteness;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public LocalDate getPeriodStart() {
    return periodStart;
  }

  public Currency getCurrency() {
    return Currency.getInstance(currency);
  }

  public Money moneyIn() {
    return Money.of(moneyInMinor, getCurrency());
  }

  public Money expense() {
    return Money.of(expenseMinor, getCurrency());
  }

  public Money savingNet() {
    return Money.of(savingNetMinor, getCurrency());
  }

  public Money investmentNet() {
    return Money.of(investmentNetMinor, getCurrency());
  }

  public Money deduction() {
    return Money.of(deductionMinor, getCurrency());
  }

  public Money stashed() {
    return Money.of(stashedMinor, getCurrency());
  }

  public Money outflow() {
    return Money.of(outflowMinor, getCurrency());
  }

  public Money gap() {
    return Money.of(gapMinor, getCurrency());
  }

  public long getMoneyInMinor() {
    return moneyInMinor;
  }

  public long getStashedMinor() {
    return stashedMinor;
  }

  public long getGapMinor() {
    return gapMinor;
  }

  public @Nullable Integer getProportionBp() {
    return proportionBp;
  }

  public @Nullable Short getScore() {
    return score;
  }

  public @Nullable ScoreBand getBand() {
    return band;
  }

  public Completeness getCompleteness() {
    return completeness;
  }

  public int getEntryCount() {
    return entryCount;
  }

  public Instant getComputedAt() {
    return computedAt;
  }
}
