package com.fieldops.equipment.dto;

import com.fieldops.equipment.domain.EquipmentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EquipmentResponse(
        UUID id,
        UUID siteId,
        String siteName,
        UUID clientId,
        String clientName,
        String name,
        String assetNumber,
        String serialNumber,
        String manufacturer,
        String model,
        String description,
        String qrCode,
        EquipmentStatus status,
        LocalDate installedAt,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
