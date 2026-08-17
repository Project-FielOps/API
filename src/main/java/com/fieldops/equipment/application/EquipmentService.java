package com.fieldops.equipment.application;

import com.fieldops.equipment.domain.Equipment;
import com.fieldops.equipment.domain.EquipmentStatus;
import com.fieldops.equipment.dto.EquipmentUpsertRequest;
import com.fieldops.equipment.repository.EquipmentRepository;
import com.fieldops.shared.exception.ConflictException;
import com.fieldops.shared.exception.NotFoundException;
import com.fieldops.site.application.SiteService;
import com.fieldops.site.domain.InspectionSite;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final SiteService siteService;

    public Equipment create(UUID siteId, EquipmentUpsertRequest request) {
        InspectionSite site = siteService.getById(siteId);
        if (equipmentRepository.existsByQrCode(request.qrCode())) {
            throw new ConflictException("QR code '" + request.qrCode() + "' is already assigned to another equipment");
        }
        Equipment equipment = new Equipment();
        equipment.setSite(site);
        apply(equipment, request);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        return equipmentRepository.save(equipment);
    }

    @Transactional(readOnly = true)
    public Equipment getById(UUID id) {
        return equipmentRepository.findById(id).orElseThrow(() -> NotFoundException.of("Equipment", id));
    }

    @Transactional(readOnly = true)
    public Equipment getByQrCode(String qrCode) {
        return equipmentRepository.findByQrCode(qrCode).orElseThrow(() -> NotFoundException.of("Equipment with QR code", qrCode));
    }

    @Transactional(readOnly = true)
    public Page<Equipment> listBySite(UUID siteId, EquipmentStatus status, Pageable pageable) {
        siteService.getById(siteId);
        if (status != null) {
            return equipmentRepository.findBySiteIdAndStatus(siteId, status, pageable);
        }
        return equipmentRepository.findBySiteId(siteId, pageable);
    }

    public Equipment update(UUID id, EquipmentUpsertRequest request) {
        Equipment equipment = getById(id);
        if (!equipment.getQrCode().equals(request.qrCode()) && equipmentRepository.existsByQrCode(request.qrCode())) {
            throw new ConflictException("QR code '" + request.qrCode() + "' is already assigned to another equipment");
        }
        apply(equipment, request);
        return equipmentRepository.save(equipment);
    }

    public Equipment updateStatus(UUID id, EquipmentStatus status) {
        Equipment equipment = getById(id);
        equipment.setStatus(status);
        return equipmentRepository.save(equipment);
    }

    private void apply(Equipment equipment, EquipmentUpsertRequest request) {
        equipment.setName(request.name());
        equipment.setAssetNumber(request.assetNumber());
        equipment.setSerialNumber(request.serialNumber());
        equipment.setManufacturer(request.manufacturer());
        equipment.setModel(request.model());
        equipment.setDescription(request.description());
        equipment.setQrCode(request.qrCode());
        equipment.setInstalledAt(request.installedAt());
    }
}
