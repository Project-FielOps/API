package com.fieldops.audit.repository;

import com.fieldops.audit.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findByInspectionIdOrderByOccurredAtAsc(UUID inspectionId);

    Page<AuditEvent> findByEntityTypeAndEntityId(String entityType, UUID entityId, Pageable pageable);

    Page<AuditEvent> findByActorId(UUID actorId, Pageable pageable);

    @Query("""
            select e from AuditEvent e
            where (:action is null or lower(e.action) like lower(concat('%', :action, '%')))
              and (:entityType is null or lower(e.entityType) like lower(concat('%', :entityType, '%')))
              and (:entityId is null or e.entityId = :entityId)
              and (:actorId is null or e.actorId = :actorId)
              and (:occurredFrom is null or e.occurredAt >= :occurredFrom)
              and (:occurredTo is null or e.occurredAt < :occurredTo)
            """)
    Page<AuditEvent> search(
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId,
            @Param("actorId") UUID actorId,
            @Param("occurredFrom") Instant occurredFrom,
            @Param("occurredTo") Instant occurredTo,
            Pageable pageable);
}
