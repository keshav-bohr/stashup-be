package com.stashup.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stashup.entry.EntryType;

/**
 * Every finder is scoped to categories the caller may see: their own plus the system set.
 *
 * <p>There is deliberately no bare {@code findById}. Constitution Principle IV requires
 * authorisation at the data-access layer, and the mechanical way to guarantee it is to make the
 * unscoped query impossible to write rather than merely discouraged. The default methods below
 * are the only entry points, and each one injects the owner scope itself so a caller cannot
 * forget to.
 */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

  @Query("""
      SELECT c FROM Category c
      WHERE c.userId IN :owners
      ORDER BY c.entryType ASC, c.name ASC
      """)
  List<Category> findByOwners(@Param("owners") List<UUID> owners);

  @Query("""
      SELECT c FROM Category c
      WHERE c.entryType = :entryType AND c.userId IN :owners
      ORDER BY c.name ASC
      """)
  List<Category> findByOwnersAndType(
      @Param("owners") List<UUID> owners, @Param("entryType") EntryType entryType);

  @Query("SELECT c FROM Category c WHERE c.id = :categoryId AND c.userId IN :owners")
  Optional<Category> findByIdAndOwners(
      @Param("categoryId") UUID categoryId, @Param("owners") List<UUID> owners);

  /** Scoped to the owner, so one user cannot delete another's category or a system one. */
  Optional<Category> findByIdAndUserId(UUID id, UUID userId);

  boolean existsByUserIdAndEntryTypeAndName(UUID userId, EntryType entryType, String name);

  default List<Category> findVisibleTo(UUID userId) {
    return findByOwners(scope(userId));
  }

  default List<Category> findVisibleToByType(UUID userId, EntryType entryType) {
    return findByOwnersAndType(scope(userId), entryType);
  }

  default Optional<Category> findVisibleToById(UUID userId, UUID categoryId) {
    return findByIdAndOwners(categoryId, scope(userId));
  }

  private static List<UUID> scope(UUID userId) {
    return List.of(userId, Category.SYSTEM_OWNER);
  }
}
