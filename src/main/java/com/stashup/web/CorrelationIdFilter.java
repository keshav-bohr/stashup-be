package com.stashup.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds a correlation ID to the logging context for the life of each request and echoes it back
 * on the response, so a single request is traceable end to end (constitution Principle VI).
 *
 * <p>An inbound correlation ID is accepted but sanitised — it lands in log output, so an
 * unvalidated header value would be a log-injection vector.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String inbound = request.getHeader(CorrelationId.HEADER);
    String correlationId =
        inbound != null && SAFE.matcher(inbound).matches()
            ? inbound
            : UUID.randomUUID().toString();

    MDC.put(CorrelationId.MDC_KEY, correlationId);
    response.setHeader(CorrelationId.HEADER, correlationId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(CorrelationId.MDC_KEY);
    }
  }
}
