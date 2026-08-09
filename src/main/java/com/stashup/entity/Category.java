package com.stashup.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.stashup.domain.EntryType;
import com.stashup.util.UuidV7;

/** A label grouping entries within a type. Either system-provided or user-created. */
@Entity
@Table(name = "category")
public class Category {

  /**
   * Owner of system categories. A sentinel rather than {@code NULL} because MySQL treats NULLs as
   * distinct in a unique index, which would let duplicate system category names through.
   */
  public static final UUID SYSTEM_OWNER = new UUID(0L, 0L);

  @Id
  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "id", columnDefinition = "BINARY(16)")
  private UUID id;

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type", nullable = false, length = 16)
  private EntryType entryType;

  @Column(name = "name", nullable = false, length = 50)
  private String name;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Category() {
    // for JPA
  }

  public static Category userDefined(UUID userId, EntryType entryType, String name, Instant now) {
    Category category = new Category();
    category.id = UuidV7.generate();
    category.userId = userId;
    category.entryType = entryType;
    category.name = name;
    category.createdAt = now;
    return category;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public EntryType getEntryType() {
    return entryType;
  }

  public String getName() {
    return name;
  }

  public boolean isSystem() {
    return SYSTEM_OWNER.equals(userId);
  }
}
