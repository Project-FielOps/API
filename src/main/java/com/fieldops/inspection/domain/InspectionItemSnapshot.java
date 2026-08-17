package com.fieldops.inspection.domain;

import com.fieldops.shared.domain.BaseEntity;
import com.fieldops.template.domain.ResponseType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Frozen copy of a template item at the moment an inspection was created (spec 10.8.3). */
@Entity
@Table(name = "inspection_item_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class InspectionItemSnapshot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    /** Historical reference only - the source TemplateItem may later be edited or deleted. */
    private UUID sourceTemplateItemId;

    @Column(nullable = false)
    private String sectionTitle;

    private String sectionDescription;

    @Column(nullable = false)
    private Integer sectionOrder;

    private String itemCode;

    @Column(nullable = false)
    private String itemTitle;

    private String itemDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResponseType responseType;

    @Column(nullable = false)
    private boolean required;

    @Column(columnDefinition = "TEXT")
    private String rulesJson;

    @Column(columnDefinition = "TEXT")
    private String optionsJson;

    @Column(nullable = false)
    private Integer itemOrder;
}
