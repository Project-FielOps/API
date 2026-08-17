package com.fieldops.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SectionUpsertRequest(
        @NotBlank(message = "title is required") String title,
        String description,
        @NotNull(message = "displayOrder is required") Integer displayOrder
) {
}
