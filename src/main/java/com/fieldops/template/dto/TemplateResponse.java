package com.fieldops.template.dto;

import com.fieldops.template.domain.TemplateStatus;

import java.time.Instant;
import java.util.UUID;

public record TemplateResponse(
        UUID id,
        String title,
        String description,
        String category,
        TemplateStatus status,
        Integer currentVersion,
        boolean hasOpenDraft,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
