package com.fieldops.inspection.domain;

import com.fieldops.client.domain.Client;
import com.fieldops.equipment.domain.Equipment;
import com.fieldops.shared.domain.BaseEntity;
import com.fieldops.site.domain.InspectionSite;
import com.fieldops.template.domain.InspectionTemplateVersion;
import com.fieldops.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "inspection")
@Getter
@Setter
@NoArgsConstructor
public class Inspection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_version_id", nullable = false)
    private InspectionTemplateVersion templateVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private InspectionSite site;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id")
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technician_id", nullable = false)
    private User technician;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supervisor_id", nullable = false)
    private User supervisor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InspectionStatus status = InspectionStatus.ASSIGNED;

    @Column(nullable = false)
    private Instant scheduledFor;

    private Instant startedAtDevice;
    private Instant startedAtServer;
    private Instant completedAtDevice;
    private Instant submittedAtServer;
    private Instant approvedAt;

    private Instant canceledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canceled_by")
    private User canceledBy;

    private String canceledReason;
}
