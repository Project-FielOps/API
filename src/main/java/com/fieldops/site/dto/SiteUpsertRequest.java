package com.fieldops.site.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record SiteUpsertRequest(
        @NotBlank(message = "name is required") String name,
        String description,
        String addressLine,
        String city,
        String state,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude,
        String contactName,
        String contactPhone
) {
}
