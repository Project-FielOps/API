package com.fieldops.audit.controller;

import com.fieldops.audit.domain.AuditEvent;
import com.fieldops.audit.dto.AuditEventResponse;
import com.fieldops.audit.repository.AuditEventRepository;
import com.fieldops.shared.dto.PageResponse;
import com.fieldops.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.stream.Collectors;

/** Read-only audit trail. Not editable/deletable by any role, per spec section 10.12. */
@RestController
@RequestMapping("/api/v1/audit-events")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
public class AuditEventController {

    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;

    @GetMapping
    public PageResponse<AuditEventResponse> list(@RequestParam(required = false) String action,
                                                  @RequestParam(required = false) String entityType,
                                                  @RequestParam(required = false) UUID entityId,
                                                  @RequestParam(required = false) UUID actorId,
                                                  @RequestParam(required = false) LocalDate occurredFrom,
                                                  @RequestParam(required = false) LocalDate occurredTo,
                                                  Pageable pageable) {
        Instant occurredFromInstant = occurredFrom == null ? null : occurredFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant occurredToInstant = occurredTo == null ? null : occurredTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        var page = auditEventRepository.search(action, entityType, entityId, actorId, occurredFromInstant, occurredToInstant, pageable);
        var actorIds = page.getContent().stream()
                .map(AuditEvent::getActorId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        var actorNames = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(user -> user.getId(), user -> user.getName()));
        return PageResponse.of(page.map(event -> toResponse(event, actorNames)));
    }

    private AuditEventResponse toResponse(AuditEvent e, java.util.Map<UUID, String> actorNames) {
        return new AuditEventResponse(e.getId(), e.getInspectionId(), e.getActorId(), e.getAction(), e.getEntityType(),
                e.getEntityId(), e.getOccurredAt(), e.getPreviousValueJson(), e.getNewValueJson(), e.getMetadataJson(),
                actorNames.get(e.getActorId()), e.getRequestId());
    }
}
