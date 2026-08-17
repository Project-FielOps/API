package com.fieldops.evidence.domain;

import com.fieldops.inspection.domain.Inspection;
import com.fieldops.inspection.domain.InspectionResponse;
import com.fieldops.shared.domain.BaseEntity;
import com.fieldops.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evidence")
@Getter
@Setter
@NoArgsConstructor
public class Evidence extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_id")
    private InspectionResponse response;

    /** Plain reference (not a JPA relation) to keep the evidence <-> review package dependency one-directional. */
    private UUID nonConformityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvidenceType type = EvidenceType.PHOTO;

    @Column(nullable = false)
    private String storageKey;

    private String localUri;

    private String originalFileName;

    private String mimeType;

    private Long sizeBytes;

    private String checksum;

    private String description;

    private BigDecimal latitude;
    private BigDecimal longitude;

    private Instant capturedAtDevice;
    private Instant serverReceivedAt;
    private Instant uploadedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
