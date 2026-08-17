package com.fieldops.equipment.controller;

import com.fieldops.equipment.application.EquipmentService;
import com.fieldops.equipment.domain.Equipment;
import com.fieldops.equipment.domain.EquipmentStatus;
import com.fieldops.equipment.dto.EquipmentResponse;
import com.fieldops.equipment.dto.EquipmentUpsertRequest;
import com.fieldops.equipment.dto.UpdateEquipmentStatusRequest;
import com.fieldops.equipment.mapper.EquipmentMapper;
import com.fieldops.shared.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;
    private final EquipmentMapper equipmentMapper;

    @PostMapping("/api/v1/sites/{siteId}/equipment")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<EquipmentResponse> create(@PathVariable UUID siteId, @Valid @RequestBody EquipmentUpsertRequest request) {
        Equipment created = equipmentService.create(siteId, request);
        return ResponseEntity.created(URI.create("/api/v1/equipment/" + created.getId()))
                .body(equipmentMapper.toResponse(created));
    }

    @GetMapping("/api/v1/sites/{siteId}/equipment")
    public PageResponse<EquipmentResponse> listBySite(@PathVariable UUID siteId,
                                                       @RequestParam(required = false) EquipmentStatus status,
                                                       Pageable pageable) {
        return PageResponse.of(equipmentService.listBySite(siteId, status, pageable).map(equipmentMapper::toResponse));
    }

    @GetMapping("/api/v1/equipment/{id}")
    public EquipmentResponse get(@PathVariable UUID id) {
        return equipmentMapper.toResponse(equipmentService.getById(id));
    }

    @GetMapping("/api/v1/equipment/by-qr/{qrCode}")
    public EquipmentResponse getByQr(@PathVariable String qrCode) {
        return equipmentMapper.toResponse(equipmentService.getByQrCode(qrCode));
    }

    @PutMapping("/api/v1/equipment/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public EquipmentResponse update(@PathVariable UUID id, @Valid @RequestBody EquipmentUpsertRequest request) {
        return equipmentMapper.toResponse(equipmentService.update(id, request));
    }

    @PatchMapping("/api/v1/equipment/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public EquipmentResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateEquipmentStatusRequest request) {
        return equipmentMapper.toResponse(equipmentService.updateStatus(id, request.status()));
    }
}
