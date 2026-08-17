package com.fieldops.template.repository;

import com.fieldops.template.domain.InspectionTemplate;
import com.fieldops.template.domain.TemplateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InspectionTemplateRepository extends JpaRepository<InspectionTemplate, UUID> {
    Page<InspectionTemplate> findByStatus(TemplateStatus status, Pageable pageable);

    Page<InspectionTemplate> findByCategoryIgnoreCase(String category, Pageable pageable);
}
