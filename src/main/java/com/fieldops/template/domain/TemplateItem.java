package com.fieldops.template.domain;

import com.fieldops.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "template_item")
@Getter
@Setter
@NoArgsConstructor
public class TemplateItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private TemplateSection section;

    private String code;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResponseType responseType;

    @Column(nullable = false)
    private boolean required = true;

    @Column(nullable = false)
    private boolean observationRequiredOnFailure = false;

    @Column(nullable = false)
    private boolean evidenceRequiredOnFailure = false;

    @Column(columnDefinition = "TEXT")
    private String optionsJson;

    @Column(nullable = false)
    private Integer displayOrder;
}
