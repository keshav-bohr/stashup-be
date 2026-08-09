package com.stashup.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Currency;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.stashup.domain.Money;
import com.stashup.dto.ReconciliationDtos.Prompt;
import com.stashup.dto.ReconciliationDtos.Resolution;
import com.stashup.service.ReconciliationPrompts;

/**
 * FR-025: the prompt must not assert or imply that the user has been dishonest.
 *
 * <p>This is the requirement most likely to drift during implementation — someone tightening the
 * copy later can easily reach for a word like "discrepancy" or "unexplained" without noticing the
 * change in tone. Wrongly accusing an honest user who genuinely spent down savings is a worse
 * outcome than letting one user post an unverified score, so the constraint is a test rather than
 * a comment.
 */
class ReconciliationCopyTest {

  /**
   * Words that accuse, suspect, or imply wrongdoing.
   *
   * <p>Matched on word boundaries, not as raw substrings: "lie" would otherwise fire on
   * "earlier", which is exactly the kind of false alarm that gets a guard test deleted.
   */
  private static final List<String> ACCUSATORY = List.of(
      "dishonest", "lying", "lie", "lies", "false", "falsified", "fraud", "cheat", "cheating",
      "suspicious", "suspect", "invalid", "wrong", "incorrect", "error", "violation", "failed",
      "discrepancy", "unexplained", "inconsistent", "mismatch", "must explain", "justify");

  private final Prompt prompt =
      ReconciliationPrompts.forGap(Money.of(50_000L, Currency.getInstance("INR")));

  @Test
  @DisplayName("the prompt contains no accusatory language")
  void promptDoesNotAccuse() {
    String message = prompt.message().toLowerCase(Locale.ROOT);

    for (String term : ACCUSATORY) {
      assertThat(message)
          .as("prompt copy must not use accusatory term '%s': \"%s\"", term, prompt.message())
          .doesNotMatch("(?s).*\\b" + java.util.regex.Pattern.quote(term) + "\\b.*");
    }
  }

  @Test
  @DisplayName("the prompt states the size of the gap so the user can act on it")
  void promptStatesTheGap() {
    assertThat(prompt.message()).contains("INR").contains("500");
  }

  @Test
  @DisplayName("exactly two resolutions are offered, never more")
  void exactlyTwoResolutions() {
    assertThat(prompt.resolutions())
        .containsExactly(Resolution.RECORD_MISSING_INCOME, Resolution.ACKNOWLEDGE_DRAWDOWN);
  }

  @Test
  @DisplayName("the copy frames the data as incomplete, not the user as at fault")
  void promptFramesDataNotPerson() {
    String message = prompt.message().toLowerCase(Locale.ROOT);

    // It should offer the benign explanations rather than demand justification.
    assertThat(message).containsAnyOf("not been entered", "set aside earlier", "easy to sort");
  }
}
