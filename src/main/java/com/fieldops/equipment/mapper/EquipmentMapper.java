package com.fieldops.equipment.mapper;

import com.fieldops.equipment.domain.Equipment;
import com.fieldops.equipment.dto.EquipmentResponse;
import org.springframework.stereotype.Component;

@Component
public class EquipmentMapper {
    public EquipmentResponse toResponse(Equipment e) {
        return new EquipmentResponse(e.getId(), e.getSite().getId(), e.getSite().getName(),
                e.getSite().getClient().getId(), e.getSite().getClient().getName(),
                e.getName(), e.getAssetNumber(), e.getSerialNumber(), e.getManufacturer(), e.getModel(),
                e.getDescription(), e.getQrCode(), e.getStatus(), e.getInstalledAt(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getVersion());
    }
}
