package com.fieldops.template.repository;

import com.fieldops.template.domain.TemplateItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TemplateItemRepository extends JpaRepository<TemplateItem, UUID> {
    List<TemplateItem> findBySectionIdOrderByDisplayOrderAsc(UUID sectionId);

    List<TemplateItem> findBySectionIdInOrderByDisplayOrderAsc(List<UUID> sectionIds);
}
