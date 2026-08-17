package com.fieldops.audit.repository;

import com.fieldops.audit.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findByInspectionIdOrderByOccurredAtAsc(UUID inspectionId);

    Page<AuditEvent> findByEntityTypeAndEntityId(String entityType, UUID entityId, Pageable pageable);

    Page<AuditEvent> findByActorId(UUID actorId, Pageable pageable);
}
