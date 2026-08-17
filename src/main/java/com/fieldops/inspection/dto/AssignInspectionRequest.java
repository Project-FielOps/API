package com.fieldops.inspection.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignInspectionRequest(
        @NotNull(message = "technicianId is required") UUID technicianId,
        @NotNull(message = "supervisorId is required") UUID supervisorId
) {
}
