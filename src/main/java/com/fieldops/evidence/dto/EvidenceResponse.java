package com.fieldops.evidence.dto;

import com.fieldops.evidence.domain.EvidenceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EvidenceResponse(
        UUID id,
        UUID inspectionId,
        UUID responseId,
        UUID nonConformityId,
        EvidenceType type,
        String originalFileName,
        String mimeType,
        Long sizeBytes,
        String checksum,
        String description,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant uploadedAt,
        UUID createdBy
) {
}
