package com.stashup.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.stashup.exception.ApiException;
import com.stashup.exception.ErrorCode;
import com.stashup.util.UuidV7;
import com.stashup.web.CursorCodec;

class CursorCodecTest {

  @Test
  void roundTripsThePosition() {
    LocalDate date = LocalDate.of(2026, 8, 9);
    UUID id = UuidV7.generate();

    CursorCodec.Cursor decoded = CursorCodec.decode(CursorCodec.encode(date, id));

    assertThat(decoded.entryDate()).isEqualTo(date);
    assertThat(decoded.id()).isEqualTo(id);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-base64!!", "", "Zm9vYmFy", "MjAyNi0wOC0wOQ"})
  @DisplayName("a malformed cursor is a 400, not a 500")
  void malformedCursorsRejected(String cursor) {
    assertThatThrownBy(() -> CursorCodec.decode(cursor))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.INVALID_CURSOR);
  }

  @Test
  @DisplayName("the cursor carries no authority; it only encodes a position")
  void cursorIsOpaqueButNotSecret() {
    String cursor = CursorCodec.encode(LocalDate.of(2026, 1, 1), UuidV7.generate());

    // Deliberately decodable: forging one can at worst reposition a caller within their own
    // data, because every query it feeds is still scoped by the authenticated user.
    assertThat(cursor).isNotBlank().doesNotContain("|");
    assertThat(CursorCodec.decode(cursor).entryDate()).isEqualTo(LocalDate.of(2026, 1, 1));
  }
}
