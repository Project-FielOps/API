package com.fieldops.template.dto;

import com.fieldops.template.domain.ResponseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ItemUpsertRequest(
        String code,
        @NotBlank(message = "title is required") String title,
        String description,
        @NotNull(message = "responseType is required") ResponseType responseType,
        boolean required,
        boolean observationRequiredOnFailure,
        boolean evidenceRequiredOnFailure,
        String optionsJson,
        @NotNull(message = "displayOrder is required") Integer displayOrder
) {
}
