package com.fieldops.inspection.domain;

import com.fieldops.shared.domain.BaseEntity;
import com.fieldops.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "inspection_response")
@Getter
@Setter
@NoArgsConstructor
public class InspectionResponse extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_item_id", nullable = false, unique = true)
    private InspectionItemSnapshot inspectionItem;

    @Column(columnDefinition = "TEXT")
    private String valueText;

    private BigDecimal valueNumber;

    private Boolean valueBoolean;

    private LocalDate valueDate;

    @Column(columnDefinition = "TEXT")
    private String valueJson;

    @Column(columnDefinition = "TEXT")
    private String observation;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Conformity conformity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answered_by")
    private User answeredBy;

    private Instant answeredAtDevice;

    private Instant serverReceivedAt;
}
