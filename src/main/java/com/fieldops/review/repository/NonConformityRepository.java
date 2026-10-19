package com.fieldops.review.repository;

import com.fieldops.review.domain.NonConformity;
import com.fieldops.review.domain.NonConformitySeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface NonConformityRepository extends JpaRepository<NonConformity, UUID>, JpaSpecificationExecutor<NonConformity> {
    List<NonConformity> findByInspectionId(UUID inspectionId);

    @Override
    @EntityGraph(attributePaths = {"inspection", "createdBy"})
    Page<NonConformity> findAll(Specification<NonConformity> specification, Pageable pageable);

    long countBySeverity(NonConformitySeverity severity);

    long countByInspectionSupervisorIdAndSeverity(UUID supervisorId, NonConformitySeverity severity);
}
