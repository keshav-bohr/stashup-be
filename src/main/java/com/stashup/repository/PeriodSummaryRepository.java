package com.stashup.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stashup.entity.PeriodSummary;

public interface PeriodSummaryRepository extends JpaRepository<PeriodSummary, UUID> {

  Optional<PeriodSummary> findByUserIdAndPeriodStart(UUID userId, LocalDate periodStart);

  List<PeriodSummary> findByUserIdAndPeriodStartBetweenOrderByPeriodStartAsc(
      UUID userId, LocalDate from, LocalDate to);

  /**
   * The comparison view's single query: every participant's row for one period, fetched together
   * rather than one lookup per friend. This is the whole reason totals are materialised.
   */
  @Query("""
      SELECT s FROM PeriodSummary s
      WHERE s.userId IN :userIds AND s.periodStart = :periodStart
      """)
  List<PeriodSummary> findForUsersInPeriod(
      @Param("userIds") Collection<UUID> userIds, @Param("periodStart") LocalDate periodStart);

  /** Bounded streak lookback for many users at once. */
  @Query("""
      SELECT s FROM PeriodSummary s
      WHERE s.userId IN :userIds AND s.periodStart BETWEEN :from AND :to
      ORDER BY s.periodStart DESC
      """)
  List<PeriodSummary> findForUsersInRange(
      @Param("userIds") Collection<UUID> userIds,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);
}
