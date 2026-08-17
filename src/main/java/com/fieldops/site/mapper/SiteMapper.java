package com.fieldops.site.mapper;

import com.fieldops.site.domain.InspectionSite;
import com.fieldops.site.dto.SiteResponse;
import org.springframework.stereotype.Component;

@Component
public class SiteMapper {
    public SiteResponse toResponse(InspectionSite s) {
        return new SiteResponse(s.getId(), s.getClient().getId(), s.getClient().getName(), s.getName(),
                s.getDescription(), s.getAddressLine(), s.getCity(), s.getState(), s.getPostalCode(),
                s.getLatitude(), s.getLongitude(), s.getContactName(), s.getContactPhone(), s.getStatus(),
                s.getCreatedAt(), s.getUpdatedAt(), s.getVersion());
    }
}
