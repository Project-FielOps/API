package com.fieldops.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail (spec 10.12). Deliberately does not use BaseEntity/JPA relations:
 * audit rows must never be edited or deleted by application code, and must be writable from
 * any bounded context without creating package cycles - so references are plain UUID columns.
 */
@Entity
@Table(name = "audit_event")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID inspectionId;

    private UUID actorId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String entityType;

    private UUID entityId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    private Instant deviceOccurredAt;

    @Column(columnDefinition = "TEXT")
    private String previousValueJson;

    @Column(columnDefinition = "TEXT")
    private String newValueJson;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    private String requestId;

    private String deviceId;
}
