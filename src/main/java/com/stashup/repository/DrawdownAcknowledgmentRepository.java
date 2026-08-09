package com.stashup.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stashup.entity.DrawdownAcknowledgment;

public interface DrawdownAcknowledgmentRepository
    extends JpaRepository<DrawdownAcknowledgment, UUID> {

  Optional<DrawdownAcknowledgment> findByUserIdAndPeriodStart(UUID userId, LocalDate periodStart);

  void deleteByUserIdAndPeriodStart(UUID userId, LocalDate periodStart);
}
