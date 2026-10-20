package com.fieldops.inspection.controller;

import com.fieldops.audit.dto.AuditEventResponse;
import com.fieldops.audit.repository.AuditEventRepository;
import com.fieldops.auth.security.FieldOpsUserDetails;
import com.fieldops.inspection.application.InspectionService;
import com.fieldops.inspection.domain.Inspection;
import com.fieldops.inspection.domain.InspectionStatus;
import com.fieldops.inspection.dto.*;
import com.fieldops.inspection.mapper.InspectionMapper;
import com.fieldops.shared.dto.PageResponse;
import com.fieldops.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspections")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionService inspectionService;
    private final InspectionMapper inspectionMapper;
    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<InspectionDetailResponse> create(@Valid @RequestBody CreateInspectionRequest request,
                                                             @AuthenticationPrincipal FieldOpsUserDetails principal) {
        Inspection created = inspectionService.create(request, principal.getUser());
        return ResponseEntity.created(URI.create("/api/v1/inspections/" + created.getId())).body(toDetail(created));
    }

    @GetMapping
    public PageResponse<InspectionSummaryResponse> list(@RequestParam(required = false) UUID technicianId,
                                                          @RequestParam(required = false) UUID supervisorId,
                                                          @RequestParam(required = false) UUID clientId,
                                                          @RequestParam(required = false) UUID siteId,
                                                          @RequestParam(required = false) InspectionStatus status,
                                                          @RequestParam(required = false) com.fieldops.inspection.domain.Priority priority,
                                                          @RequestParam(required = false) java.time.Instant scheduledFrom,
                                                          @RequestParam(required = false) java.time.Instant scheduledTo,
                                                          @RequestParam(defaultValue = "false") boolean overdue,
                                                          @RequestParam(defaultValue = "false") boolean awaitingReview,
                                                          Pageable pageable,
                                                          @AuthenticationPrincipal FieldOpsUserDetails principal) {
        return PageResponse.of(inspectionService.listForActor(principal.getUser(), technicianId, supervisorId, clientId, siteId,
                        status, priority, scheduledFrom, scheduledTo, overdue, awaitingReview, pageable)
                .map(inspectionMapper::toSummary));
    }

    @GetMapping("/{id}")
    public InspectionDetailResponse get(@PathVariable UUID id, @AuthenticationPrincipal FieldOpsUserDetails principal) {
        return toDetail(inspectionService.getAuthorized(id, principal.getUser()));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public InspectionDetailResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignInspectionRequest request,
                                            @AuthenticationPrincipal FieldOpsUserDetails principal) {
        return toDetail(inspectionService.assign(id, request.technicianId(), request.supervisorId(), principal.getUser()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public InspectionDetailResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelInspectionRequest request,
                                            @AuthenticationPrincipal FieldOpsUserDetails principal) {
        return toDetail(inspectionService.cancel(id, request.reason(), principal.getUser()));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public InspectionDetailResponse start(@PathVariable UUID id, @AuthenticationPrincipal FieldOpsUserDetails principal) {
        return toDetail(inspectionService.start(id, principal.getUser()));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public InspectionDetailResponse submit(@PathVariable UUID id, @AuthenticationPrincipal FieldOpsUserDetails principal) {
        return toDetail(inspectionService.submit(id, principal.getUser()));
    }

    @PostMapping("/{id}/reopen-correction")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public InspectionDetailResponse reopenCorrection(@PathVariable UUID id,
                                                       @AuthenticationPrincipal FieldOpsUserDetails principal) {
        return toDetail(inspectionService.reopenCorrection(id, principal.getUser()));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public List<AuditEventResponse> history(@PathVariable UUID id, @AuthenticationPrincipal FieldOpsUserDetails principal) {
        inspectionService.getAuthorized(id, principal.getUser());
        return auditEventRepository.findByInspectionIdOrderByOccurredAtAsc(id).stream()
                .map(e -> new AuditEventResponse(e.getId(), e.getInspectionId(), e.getActorId(), e.getAction(), e.getEntityType(),
                        e.getEntityId(), e.getOccurredAt(), e.getPreviousValueJson(), e.getNewValueJson(), e.getMetadataJson(),
                        e.getActorId() == null ? null : userRepository.findById(e.getActorId()).map(user -> user.getName()).orElse(null),
                        e.getRequestId()))
                .toList();
    }

    private InspectionDetailResponse toDetail(Inspection inspection) {
        return inspectionMapper.toDetail(inspection, inspectionService.getSnapshots(inspection.getId()),
                inspectionService.getAnswersByItemId(inspection.getId()));
    }
}
