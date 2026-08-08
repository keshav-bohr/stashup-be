package com.stashup.entry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every finder takes the owning user's ID.
 *
 * <p>There is no {@code findById(UUID)} on this interface, and adding one is a review failure.
 * Constitution Principle IV requires authorisation at the data-access layer: a query that could
 * return another user's row must be impossible to write, not merely discouraged.
 */
public interface FinancialEntryRepository extends JpaRepository<FinancialEntry, UUID> {

  Optional<FinancialEntry> findByIdAndUserId(UUID id, UUID userId);

  long countByUserIdAndCategoryId(UUID userId, UUID categoryId);

  /**
   * Keyset page over {@code (entry_date DESC, id DESC)}, matching {@code ix_entry_user_date}.
   * Filters are all optional; a null filter matches everything.
   */
  @Query("""
      SELECT e FROM FinancialEntry e
      WHERE e.userId = :userId
        AND (:from IS NULL OR e.entryDate >= :from)
        AND (:to IS NULL OR e.entryDate <= :to)
        AND (:entryType IS NULL OR e.entryType = :entryType)
        AND (:categoryId IS NULL OR e.categoryId = :categoryId)
        AND (:cursorDate IS NULL
             OR e.entryDate < :cursorDate
             OR (e.entryDate = :cursorDate AND e.id < :cursorId))
      ORDER BY e.entryDate DESC, e.id DESC
      """)
  List<FinancialEntry> findPage(
      @Param("userId") UUID userId,
      @Param("from") @Nullable LocalDate from,
      @Param("to") @Nullable LocalDate to,
      @Param("entryType") @Nullable EntryType entryType,
      @Param("categoryId") @Nullable UUID categoryId,
      @Param("cursorDate") @Nullable LocalDate cursorDate,
      @Param("cursorId") @Nullable UUID cursorId,
      Limit limit);

  /**
   * The monthly rollup: one indexed {@code GROUP BY} over a single user's single month.
   *
   * <p>Withdrawals are negated here so savings and investments arrive already netted, which is
   * what FR-009 requires and what stops money cycled in and out from inflating a score.
   */
  @Query("""
      SELECT e.entryType,
             SUM(CASE WHEN e.direction = com.stashup.entry.Direction.WITHDRAWAL
                      THEN -e.amountMinor ELSE e.amountMinor END),
             COUNT(e)
      FROM FinancialEntry e
      WHERE e.userId = :userId AND e.entryDate BETWEEN :periodStart AND :periodEnd
      GROUP BY e.entryType
      """)
  List<Object[]> aggregateByType(
      @Param("userId") UUID userId,
      @Param("periodStart") LocalDate periodStart,
      @Param("periodEnd") LocalDate periodEnd);

  /**
   * Category breakdown, computed on demand rather than materialised: it is only ever read by the
   * single owning user for a single period, so an indexed GROUP BY is cheaper than maintaining
   * another table.
   */
  @Query("""
      SELECT e.categoryId,
             SUM(CASE WHEN e.direction = com.stashup.entry.Direction.WITHDRAWAL
                      THEN -e.amountMinor ELSE e.amountMinor END),
             COUNT(e)
      FROM FinancialEntry e
      WHERE e.userId = :userId AND e.entryDate BETWEEN :periodStart AND :periodEnd
      GROUP BY e.categoryId
      """)
  List<Object[]> aggregateByCategory(
      @Param("userId") UUID userId,
      @Param("periodStart") LocalDate periodStart,
      @Param("periodEnd") LocalDate periodEnd);

  /** Distinct months in which this user has any entry — used to drive yearly rollups. */
  @Query("""
      SELECT DISTINCT e.entryDate FROM FinancialEntry e
      WHERE e.userId = :userId AND e.entryDate BETWEEN :from AND :to
      """)
  List<LocalDate> findDatesInRange(
      @Param("userId") UUID userId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);
}
