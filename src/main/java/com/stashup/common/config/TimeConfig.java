package com.stashup.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A single injected {@link Clock} rather than scattered {@code Instant.now()} calls.
 *
 * <p>Scoring, reconciliation, and lockout are all time-dependent, and tests need to control
 * "now" to assert period boundaries and expiry without sleeping.
 */
@Configuration
public class TimeConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
