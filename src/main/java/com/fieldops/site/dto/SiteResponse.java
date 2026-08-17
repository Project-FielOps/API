package com.fieldops.site.dto;

import com.fieldops.site.domain.SiteStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SiteResponse(
        UUID id,
        UUID clientId,
        String clientName,
        String name,
        String description,
        String addressLine,
        String city,
        String state,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude,
        String contactName,
        String contactPhone,
        SiteStatus status,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
