package com.stashup.common.page;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * One page of results plus the cursor for the next.
 *
 * @param nextCursor {@code null} when this is the last page
 */
public record KeysetPage<T>(List<T> items, @Nullable String nextCursor) {

  public KeysetPage {
    items = List.copyOf(items);
  }

  public static <T> KeysetPage<T> last(List<T> items) {
    return new KeysetPage<>(items, null);
  }

  public <R> KeysetPage<R> map(java.util.function.Function<T, R> mapper) {
    return new KeysetPage<>(items.stream().map(mapper).toList(), nextCursor);
  }
}
