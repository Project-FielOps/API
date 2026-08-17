package com.fieldops.site.dto;

import com.fieldops.site.domain.SiteStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSiteStatusRequest(
        @NotNull(message = "status is required") SiteStatus status
) {
}
