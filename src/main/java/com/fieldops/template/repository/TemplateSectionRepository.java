package com.fieldops.template.repository;

import com.fieldops.template.domain.TemplateSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TemplateSectionRepository extends JpaRepository<TemplateSection, UUID> {
    List<TemplateSection> findByTemplateVersionIdOrderByDisplayOrderAsc(UUID templateVersionId);
}
