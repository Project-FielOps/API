package com.fieldops.template.dto;

import com.fieldops.template.domain.ResponseType;

import java.util.UUID;

public record TemplateItemResponse(
        UUID id,
        String code,
        String title,
        String description,
        ResponseType responseType,
        boolean required,
        boolean observationRequiredOnFailure,
        boolean evidenceRequiredOnFailure,
        String optionsJson,
        Integer displayOrder
) {
}
