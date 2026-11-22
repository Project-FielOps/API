package com.fieldops.template.repository;

import com.fieldops.template.domain.InspectionTemplate;
import com.fieldops.template.domain.TemplateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface InspectionTemplateRepository extends JpaRepository<InspectionTemplate, UUID> {
    Page<InspectionTemplate> findByStatus(TemplateStatus status, Pageable pageable);

    Page<InspectionTemplate> findByCategoryIgnoreCase(String category, Pageable pageable);

    Page<InspectionTemplate> findByStatusAndCategoryIgnoreCase(TemplateStatus status, String category, Pageable pageable);

    @Query("select distinct t.category from InspectionTemplate t where t.category is not null and trim(t.category) <> '' order by t.category")
    List<String> findDistinctCategories();
}
