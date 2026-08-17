package com.fieldops.site.controller;

import com.fieldops.shared.dto.PageResponse;
import com.fieldops.site.application.SiteService;
import com.fieldops.site.domain.InspectionSite;
import com.fieldops.site.domain.SiteStatus;
import com.fieldops.site.dto.SiteResponse;
import com.fieldops.site.dto.SiteUpsertRequest;
import com.fieldops.site.dto.UpdateSiteStatusRequest;
import com.fieldops.site.mapper.SiteMapper;
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
public class SiteController {

    private final SiteService siteService;
    private final SiteMapper siteMapper;

    @PostMapping("/api/v1/clients/{clientId}/sites")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<SiteResponse> create(@PathVariable UUID clientId, @Valid @RequestBody SiteUpsertRequest request) {
        InspectionSite created = siteService.create(clientId, request);
        return ResponseEntity.created(URI.create("/api/v1/sites/" + created.getId()))
                .body(siteMapper.toResponse(created));
    }

    @GetMapping("/api/v1/clients/{clientId}/sites")
    public PageResponse<SiteResponse> listByClient(@PathVariable UUID clientId,
                                                    @RequestParam(required = false) SiteStatus status,
                                                    Pageable pageable) {
        return PageResponse.of(siteService.listByClient(clientId, status, pageable).map(siteMapper::toResponse));
    }

    @GetMapping("/api/v1/sites/{id}")
    public SiteResponse get(@PathVariable UUID id) {
        return siteMapper.toResponse(siteService.getById(id));
    }

    @PutMapping("/api/v1/sites/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public SiteResponse update(@PathVariable UUID id, @Valid @RequestBody SiteUpsertRequest request) {
        return siteMapper.toResponse(siteService.update(id, request));
    }

    @PatchMapping("/api/v1/sites/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public SiteResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateSiteStatusRequest request) {
        return siteMapper.toResponse(siteService.updateStatus(id, request.status()));
    }
}
