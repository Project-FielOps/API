package com.fieldops.audit.controller;

import com.fieldops.audit.domain.AuditEvent;
import com.fieldops.audit.dto.AuditEventResponse;
import com.fieldops.audit.repository.AuditEventRepository;
import com.fieldops.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Read-only audit trail. Not editable/deletable by any role, per spec section 10.12. */
@RestController
@RequestMapping("/api/v1/audit-events")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
public class AuditEventController {

    private final AuditEventRepository auditEventRepository;

    @GetMapping
    public PageResponse<AuditEventResponse> list(@RequestParam(required = false) String entityType,
                                                  @RequestParam(required = false) UUID entityId,
                                                  @RequestParam(required = false) UUID actorId,
                                                  Pageable pageable) {
        var page = (entityType != null && entityId != null)
                ? auditEventRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable)
                : (actorId != null ? auditEventRepository.findByActorId(actorId, pageable) : auditEventRepository.findAll(pageable));
        return PageResponse.of(page.map(this::toResponse));
    }

    private AuditEventResponse toResponse(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getInspectionId(), e.getActorId(), e.getAction(), e.getEntityType(),
                e.getEntityId(), e.getOccurredAt(), e.getPreviousValueJson(), e.getNewValueJson(), e.getMetadataJson(), e.getRequestId());
    }
}
