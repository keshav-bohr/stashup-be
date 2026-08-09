package com.stashup.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates time-ordered UUIDv7 identifiers (RFC 9562).
 *
 * <p>Chosen over UUIDv4 because the leading 48-bit timestamp keeps InnoDB's clustered index
 * near-sequential on insert, and over auto-increment integers because those are guessable and
 * enumerable when exposed to clients.
 */
public final class UuidV7 {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int VERSION_7 = 0x7000;
  private static final long VARIANT_RFC = 0x8000000000000000L;

  private UuidV7() {}

  public static UUID generate() {
    return generate(System.currentTimeMillis());
  }

  static UUID generate(long epochMillis) {
    byte[] randomBytes = new byte[10];
    RANDOM.nextBytes(randomBytes);

    // 48 bits of millisecond timestamp, then 4 version bits, then 12 random bits.
    long most = (epochMillis & 0xFFFFFFFFFFFFL) << 16;
    most |= VERSION_7;
    most |= ((randomBytes[0] & 0x0FL) << 8) | (randomBytes[1] & 0xFFL);

    // Variant bits then 62 random bits.
    long least = VARIANT_RFC;
    least |= (randomBytes[2] & 0x3FL) << 56;
    for (int i = 3; i < 10; i++) {
      least |= (randomBytes[i] & 0xFFL) << ((9 - i) * 8);
    }
    return new UUID(most, least);
  }
}
