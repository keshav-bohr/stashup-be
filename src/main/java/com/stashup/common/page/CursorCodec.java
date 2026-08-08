package com.stashup.common.page;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

import com.stashup.common.error.ApiException;
import com.stashup.common.error.ErrorCode;

/**
 * Encodes and decodes the opaque keyset cursor for entry listing.
 *
 * <p>The cursor is the {@code (entryDate, id)} pair of the last row on the previous page, which
 * matches the {@code (user_id, entry_date DESC, id DESC)} index. Keyset rather than offset so
 * that paging deep into a long history stays index-backed instead of degrading linearly.
 *
 * <p>It is base64 only to signal opacity — it is not a secret and carries no authority. Every
 * query it feeds is still scoped by the authenticated user, so a forged cursor can at worst
 * reposition a caller within their own data.
 */
public final class CursorCodec {

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  private CursorCodec() {}

  public static String encode(LocalDate date, UUID id) {
    String raw = date.toString() + '|' + id;
    return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  public static Cursor decode(String cursor) {
    try {
      String raw = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
      int separator = raw.indexOf('|');
      if (separator < 0) {
        throw new ApiException(ErrorCode.INVALID_CURSOR);
      }
      LocalDate date = LocalDate.parse(raw.substring(0, separator));
      UUID id = UUID.fromString(raw.substring(separator + 1));
      return new Cursor(date, id);
    } catch (IllegalArgumentException | DateTimeParseException ex) {
      throw new ApiException(ErrorCode.INVALID_CURSOR);
    }
  }

  /** The position of the last row on the previous page. */
  public record Cursor(LocalDate entryDate, UUID id) {}
}
