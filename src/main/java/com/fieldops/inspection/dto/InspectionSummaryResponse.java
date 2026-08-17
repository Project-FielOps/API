package com.fieldops.inspection.dto;

import com.fieldops.inspection.domain.InspectionStatus;
import com.fieldops.inspection.domain.Priority;

import java.time.Instant;
import java.util.UUID;

public record InspectionSummaryResponse(
        UUID id,
        String title,
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
        Priority priority,
        InspectionStatus status,
        Instant scheduledFor,
        Integer templateVersionNumber,
        String templateTitle
) {
}
