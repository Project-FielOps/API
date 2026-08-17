package com.fieldops.review.domain;

import com.fieldops.inspection.domain.Inspection;
import com.fieldops.inspection.domain.InspectionItemSnapshot;
import com.fieldops.inspection.domain.InspectionResponse;
import com.fieldops.shared.domain.BaseEntity;
import com.fieldops.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "non_conformity")
@Getter
@Setter
@NoArgsConstructor
public class NonConformity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_item_id")
    private InspectionItemSnapshot inspectionItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_id")
    private InspectionResponse response;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NonConformitySeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NonConformityStatus status = NonConformityStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    private Instant createdAtDevice;

    private Instant serverReceivedAt;
}
