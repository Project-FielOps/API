package com.fieldops.template.domain;

import com.fieldops.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "template_section")
@Getter
@Setter
@NoArgsConstructor
public class TemplateSection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_version_id", nullable = false)
    private InspectionTemplateVersion templateVersion;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private Integer displayOrder;
}
