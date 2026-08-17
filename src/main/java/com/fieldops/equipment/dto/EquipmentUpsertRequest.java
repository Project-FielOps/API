package com.fieldops.equipment.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record EquipmentUpsertRequest(
        @NotBlank(message = "name is required") String name,
        String assetNumber,
        String serialNumber,
        String manufacturer,
        String model,
        String description,
        @NotBlank(message = "qrCode is required") String qrCode,
        LocalDate installedAt
) {
}
