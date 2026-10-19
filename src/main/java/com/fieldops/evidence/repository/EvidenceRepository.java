package com.fieldops.evidence.repository;

import com.fieldops.evidence.domain.Evidence;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {
    List<Evidence> findByInspectionId(UUID inspectionId);

    @EntityGraph(attributePaths = "inspection")
    Optional<Evidence> findWithInspectionById(UUID id);

    List<Evidence> findByResponseId(UUID responseId);

    List<Evidence> findByNonConformityId(UUID nonConformityId);

    long countByInspectionId(UUID inspectionId);
}
