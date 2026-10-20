package com.fieldops.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        UUID inspectionId,
        UUID actorId,
        String action,
        String entityType,
        UUID entityId,
        Instant occurredAt,
        String previousValueJson,
        String newValueJson,
        String metadataJson,
        String actorName,
        String requestId
) {
}
