package com.fieldops.inspection.dto;

import com.fieldops.inspection.domain.Conformity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AnswerResponse(
        UUID id,
        UUID inspectionItemId,
        String valueText,
        BigDecimal valueNumber,
        Boolean valueBoolean,
        LocalDate valueDate,
        String valueJson,
        String observation,
        Conformity conformity,
        UUID answeredBy,
        Instant serverReceivedAt,
        Long version
) {
}
