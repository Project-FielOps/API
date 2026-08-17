package com.fieldops.inspection.dto;

import com.fieldops.inspection.domain.InspectionStatus;
import com.fieldops.inspection.domain.Priority;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InspectionDetailResponse(
        UUID id,
        UUID templateVersionId,
        UUID templateId,
        Integer templateVersionNumber,
        String templateTitle,
        UUID clientId,
        String clientName,
        UUID siteId,
        String siteName,
        UUID equipmentId,
        String equipmentName,
        UUID technicianId,
        String technicianName,
        UUID supervisorId,
        String supervisorName,
        UUID createdBy,
        String title,
        String instructions,
        Priority priority,
        InspectionStatus status,
        Instant scheduledFor,
        Instant startedAtServer,
        Instant submittedAtServer,
        Instant approvedAt,
        Instant canceledAt,
        UUID canceledBy,
        String canceledReason,
        Instant createdAt,
        Instant updatedAt,
        Long version,
        List<ItemSnapshotResponse> items
) {
}
