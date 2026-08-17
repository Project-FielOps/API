package com.fieldops.audit.application;

import com.fieldops.audit.domain.AuditEvent;
import com.fieldops.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    /**
     * Records an audit event. Participates in the caller's transaction (default REQUIRED) so that
     * the audit row is only persisted if the business action it documents actually commits.
     */
    @Transactional
    public void record(UUID inspectionId, UUID actorId, String action, String entityType, UUID entityId,
                        String previousValueJson, String newValueJson, String metadataJson) {
        AuditEvent event = new AuditEvent();
        event.setInspectionId(inspectionId);
        event.setActorId(actorId);
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setPreviousValueJson(previousValueJson);
        event.setNewValueJson(newValueJson);
        event.setMetadataJson(metadataJson);
        auditEventRepository.save(event);
    }

    public void record(UUID inspectionId, UUID actorId, String action, String entityType, UUID entityId) {
        record(inspectionId, actorId, action, entityType, entityId, null, null, null);
    }
}
