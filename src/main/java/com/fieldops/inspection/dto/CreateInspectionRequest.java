package com.fieldops.inspection.dto;

import com.fieldops.inspection.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateInspectionRequest(
        @NotNull(message = "templateId is required") UUID templateId,
        Integer templateVersionNumber,
        @NotNull(message = "clientId is required") UUID clientId,
        @NotNull(message = "siteId is required") UUID siteId,
        UUID equipmentId,
        @NotNull(message = "technicianId is required") UUID technicianId,
        @NotNull(message = "supervisorId is required") UUID supervisorId,
        @NotBlank(message = "title is required") String title,
        String instructions,
        Priority priority,
        @NotNull(message = "scheduledFor is required") Instant scheduledFor
) {
}
