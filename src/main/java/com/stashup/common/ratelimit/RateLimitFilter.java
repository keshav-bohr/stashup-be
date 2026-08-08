package com.stashup.common.ratelimit;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.stashup.common.correlation.CorrelationId;
import com.stashup.common.error.ErrorCode;

/** Applies the rate limit to credential endpoints and to every state-changing request. */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private static final Set<String> AUTH_PATHS =
      Set.of("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh");
  private static final Set<HttpMethod> MUTATING =
      Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

  private final RateLimiter rateLimiter;

  public RateLimitFilter(RateLimiter rateLimiter) {
    this.rateLimiter = rateLimiter;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    RateLimiter.Tier tier = tierFor(request);
    if (tier != null && !rateLimiter.tryConsume(bucketKey(request), tier)) {
      writeRateLimited(response);
      return;
    }
    chain.doFilter(request, response);
  }

  private static RateLimiter.Tier tierFor(HttpServletRequest request) {
    if (AUTH_PATHS.contains(request.getRequestURI())) {
      return RateLimiter.Tier.AUTH;
    }
    HttpMethod method = HttpMethod.valueOf(request.getMethod());
    return MUTATING.contains(method) ? RateLimiter.Tier.WRITE : null;
  }

  /**
   * Keyed on the authenticated principal where present, falling back to the remote address.
   * Using the principal means one abusive client cannot exhaust the budget for everyone behind
   * the same NAT.
   */
  private static String bucketKey(HttpServletRequest request) {
    String principal = request.getUserPrincipal() == null
        ? null
        : request.getUserPrincipal().getName();
    return principal != null ? "user:" + principal : "ip:" + request.getRemoteAddr();
  }

  private static void writeRateLimited(HttpServletResponse response) throws IOException {
    response.setStatus(ErrorCode.RATE_LIMITED.status().value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setHeader("Retry-After", "60");
    response.getWriter().write("""
        {"type":"https://stashup.com/problems/rate_limited",\
        "title":"Too many requests","status":429,\
        "code":"RATE_LIMITED","correlationId":"%s"}"""
        .formatted(CorrelationId.current()));
  }
}
