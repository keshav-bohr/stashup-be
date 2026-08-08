package com.stashup.user;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import com.stashup.common.id.UuidV7;

/** A person using the application. Owns every entry, summary, and score in the system. */
@Entity
@Table(name = "app_user")
public class AppUser {

  @Id
  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "id", columnDefinition = "BINARY(16)")
  private UUID id;

  @Column(name = "email", nullable = false, length = 320)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(name = "display_name", nullable = false, length = 50)
  private String displayName;

  /** Immutable after registration; there is no correct migration for changing it. */
  @Column(name = "base_currency", nullable = false, length = 3)
  private String baseCurrency;

  @Column(name = "timezone", nullable = false, length = 64)
  private String timezone;

  @Column(name = "failed_login_count", nullable = false)
  private int failedLoginCount;

  @Column(name = "locked_until")
  private @Nullable Instant lockedUntil;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected AppUser() {
    // for JPA
  }

  private AppUser(
      UUID id,
      String email,
      String passwordHash,
      String displayName,
      Currency baseCurrency,
      String timezone,
      Instant now) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.displayName = displayName;
    this.baseCurrency = baseCurrency.getCurrencyCode();
    this.timezone = timezone;
    this.failedLoginCount = 0;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public static AppUser register(
      String email,
      String passwordHash,
      String displayName,
      Currency baseCurrency,
      String timezone,
      Instant now) {
    return new AppUser(
        UuidV7.generate(),
        email.toLowerCase(java.util.Locale.ROOT),
        passwordHash,
        displayName,
        baseCurrency,
        timezone,
        now);
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public Currency getBaseCurrency() {
    return Currency.getInstance(baseCurrency);
  }

  public String getTimezone() {
    return timezone;
  }

  public int getFailedLoginCount() {
    return failedLoginCount;
  }

  public @Nullable Instant getLockedUntil() {
    return lockedUntil;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void rename(String newDisplayName, Instant now) {
    this.displayName = newDisplayName;
    this.updatedAt = now;
  }

  public void changeTimezone(String newTimezone, Instant now) {
    this.timezone = newTimezone;
    this.updatedAt = now;
  }

  public boolean isLockedAt(Instant now) {
    return lockedUntil != null && lockedUntil.isAfter(now);
  }

  public void recordFailedLogin(int maxAttempts, java.time.Duration lockoutDuration, Instant now) {
    this.failedLoginCount++;
    if (this.failedLoginCount >= maxAttempts) {
      this.lockedUntil = now.plus(lockoutDuration);
      this.failedLoginCount = 0;
    }
    this.updatedAt = now;
  }

  public void recordSuccessfulLogin(Instant now) {
    this.failedLoginCount = 0;
    this.lockedUntil = null;
    this.updatedAt = now;
  }
}
