package com.fieldops.evidence.repository;

import com.fieldops.evidence.domain.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {
    List<Evidence> findByInspectionId(UUID inspectionId);

    List<Evidence> findByResponseId(UUID responseId);

    List<Evidence> findByNonConformityId(UUID nonConformityId);
}
