package com.fieldops.inspection.repository;

import com.fieldops.inspection.domain.InspectionItemSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InspectionItemSnapshotRepository extends JpaRepository<InspectionItemSnapshot, UUID> {
    List<InspectionItemSnapshot> findByInspectionIdOrderBySectionOrderAscItemOrderAsc(UUID inspectionId);
}
