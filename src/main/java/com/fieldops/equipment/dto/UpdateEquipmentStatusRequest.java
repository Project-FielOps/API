package com.fieldops.equipment.dto;

import com.fieldops.equipment.domain.EquipmentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateEquipmentStatusRequest(
        @NotNull(message = "status is required") EquipmentStatus status
) {
}
