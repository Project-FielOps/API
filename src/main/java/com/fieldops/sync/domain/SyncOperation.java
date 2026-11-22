package com.fieldops.sync.domain;

import com.fieldops.shared.domain.BaseEntity;
import com.fieldops.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_operation", uniqueConstraints = @UniqueConstraint(name = "uk_sync_operation_identity",
        columnNames = {"user_id", "device_id", "operation_id"}))
@Getter
@Setter
@NoArgsConstructor
public class SyncOperation extends BaseEntity {
    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "base_version")
    private Long baseVersion;

    @Column(name = "depends_on", columnDefinition = "TEXT")
    private String dependsOn;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SyncOperationStatus status;

    @Column(name = "result_code", length = 80)
    private String resultCode;

    @Column(name = "result_payload", columnDefinition = "TEXT")
    private String resultPayload;

    @Column(nullable = false)
    private Integer attempts;

    @Column(name = "processed_at")
    private Instant processedAt;
}
