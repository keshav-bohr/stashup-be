package com.stashup.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.stashup.domain.CurrencyMismatchException;
import com.stashup.domain.Money;

class MoneyTest {

  private static final Currency INR = Currency.getInstance("INR");
  private static final Currency USD = Currency.getInstance("USD");

  @Test
  @DisplayName("combining different currencies fails rather than producing a wrong total")
  void currencyMismatchIsRejected() {
    Money rupees = Money.of(1_000L, INR);
    Money dollars = Money.of(1_000L, USD);

    assertThatThrownBy(() -> rupees.plus(dollars))
        .isInstanceOf(CurrencyMismatchException.class)
        .hasMessageContaining("INR")
        .hasMessageContaining("USD");
  }

  @Test
  @DisplayName("arithmetic is exact in minor units")
  void arithmeticIsExact() {
    Money a = Money.of(1_999_999_999L, INR);
    Money b = Money.of(1L, INR);

    assertThat(a.plus(b).amountMinor()).isEqualTo(2_000_000_000L);
    assertThat(a.minus(b).amountMinor()).isEqualTo(1_999_999_998L);
    assertThat(a.negated().amountMinor()).isEqualTo(-1_999_999_999L);
  }

  @Test
  @DisplayName("overflow throws rather than silently wrapping")
  void overflowIsDetected() {
    Money huge = Money.of(Long.MAX_VALUE, INR);

    assertThatThrownBy(() -> huge.plus(Money.of(1L, INR)))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void flooringAtZeroWorks() {
    assertThat(Money.of(-500L, INR).atLeastZero().amountMinor()).isZero();
    assertThat(Money.of(500L, INR).atLeastZero().amountMinor()).isEqualTo(500L);
  }

  @Test
  void comparisonsRequireMatchingCurrency() {
    assertThat(Money.of(10L, INR).isGreaterThan(Money.of(5L, INR))).isTrue();
    assertThatThrownBy(() -> Money.of(10L, INR).isGreaterThan(Money.of(5L, USD)))
        .isInstanceOf(CurrencyMismatchException.class);
  }
}
