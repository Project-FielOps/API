package com.fieldops.template.dto;

import java.util.List;
import java.util.UUID;

public record TemplateSectionResponse(
        UUID id,
        String title,
        String description,
        Integer displayOrder,
        List<TemplateItemResponse> items
) {
}
