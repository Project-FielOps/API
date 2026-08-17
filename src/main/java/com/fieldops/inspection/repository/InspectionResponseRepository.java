package com.fieldops.inspection.repository;

import com.fieldops.inspection.domain.InspectionResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionResponseRepository extends JpaRepository<InspectionResponse, UUID> {
    List<InspectionResponse> findByInspectionId(UUID inspectionId);

    Optional<InspectionResponse> findByInspectionItemId(UUID inspectionItemId);
}
