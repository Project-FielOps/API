package com.fieldops.template.domain;

import com.fieldops.shared.domain.BaseEntity;
import com.fieldops.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A published (or currently-being-drafted) snapshot of a template's content (spec 10.7.2).
 * {@code publishedAt == null} marks the single "open draft" version that admins may still edit;
 * once {@code publishedAt} is set the version and its sections/items become immutable.
 */
@Entity
@Table(name = "inspection_template_version")
@Getter
@Setter
@NoArgsConstructor
public class InspectionTemplateVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private InspectionTemplate template;

    @Column(nullable = false)
    private Integer versionNumber;

    @Column(nullable = false)
    private String titleSnapshot;

    private String descriptionSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    private User publishedBy;

    private Instant publishedAt;

    @Column(nullable = false)
    private boolean activeForNewInspections = false;

    public boolean isPublished() {
        return publishedAt != null;
    }
}
