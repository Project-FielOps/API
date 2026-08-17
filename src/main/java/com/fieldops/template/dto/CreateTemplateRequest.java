package com.fieldops.template.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTemplateRequest(
        @NotBlank(message = "title is required") String title,
        String description,
        @NotBlank(message = "category is required") String category
) {
}
