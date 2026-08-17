package com.fieldops.evidence.mapper;

import com.fieldops.evidence.domain.Evidence;
import com.fieldops.evidence.dto.EvidenceResponse;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class EvidenceMapper {
    public EvidenceResponse toResponse(Evidence e) {
        return new EvidenceResponse(e.getId(), e.getInspection().getId(),
                Optional.ofNullable(e.getResponse()).map(r -> r.getId()).orElse(null),
                e.getNonConformityId(), e.getType(), e.getOriginalFileName(), e.getMimeType(), e.getSizeBytes(),
                e.getChecksum(), e.getDescription(), e.getLatitude(), e.getLongitude(), e.getUploadedAt(),
                e.getCreatedBy().getId());
    }
}
