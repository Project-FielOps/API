package com.fieldops.template.repository;

import com.fieldops.template.domain.InspectionTemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionTemplateVersionRepository extends JpaRepository<InspectionTemplateVersion, UUID> {
    Optional<InspectionTemplateVersion> findByTemplateIdAndPublishedAtIsNull(UUID templateId);

    List<InspectionTemplateVersion> findByTemplateIdAndPublishedAtIsNotNullOrderByVersionNumberDesc(UUID templateId);

    Optional<InspectionTemplateVersion> findByTemplateIdAndVersionNumber(UUID templateId, Integer versionNumber);

    Optional<InspectionTemplateVersion> findTopByTemplateIdAndPublishedAtIsNotNullOrderByVersionNumberDesc(UUID templateId);
}
