package com.stashup.common.page;

import org.springframework.stereotype.Component;

import com.stashup.common.config.ApplicationProperties;
import com.stashup.common.error.ApiException;
import com.stashup.common.error.ErrorCode;

/**
 * Enforces the maximum page size.
 *
 * <p>An over-limit request is <em>rejected</em> rather than silently clamped. Clamping would let
 * a client believe it had received everything it asked for; the constitution's rule against
 * unbounded collections is only meaningful if the boundary is visible to the caller.
 */
@Component
public class PageLimit {

  private final int maxPageSize;

  public PageLimit(ApplicationProperties properties) {
    this.maxPageSize = properties.pagination().maxPageSize();
  }

  public int validate(Integer requested) {
    if (requested == null) {
      return Math.min(50, maxPageSize);
    }
    if (requested < 1 || requested > maxPageSize) {
      throw new ApiException(
          ErrorCode.PAGE_SIZE_EXCEEDED, "limit must be between 1 and " + maxPageSize);
    }
    return requested;
  }

  public int maxPageSize() {
    return maxPageSize;
  }
}
