package com.fieldops.template.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TemplateVersionResponse(
        UUID id,
        UUID templateId,
        Integer versionNumber,
        String titleSnapshot,
        String descriptionSnapshot,
        UUID publishedBy,
        Instant publishedAt,
        boolean activeForNewInspections,
        boolean published,
        List<TemplateSectionResponse> sections
) {
}
