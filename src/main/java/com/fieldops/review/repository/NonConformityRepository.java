package com.fieldops.review.repository;

import com.fieldops.review.domain.NonConformity;
import com.fieldops.review.domain.NonConformitySeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NonConformityRepository extends JpaRepository<NonConformity, UUID> {
    List<NonConformity> findByInspectionId(UUID inspectionId);

    long countBySeverity(NonConformitySeverity severity);

    long countByInspectionSupervisorIdAndSeverity(UUID supervisorId, NonConformitySeverity severity);
}
