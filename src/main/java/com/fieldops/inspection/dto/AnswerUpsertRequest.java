package com.fieldops.inspection.dto;

import com.fieldops.inspection.domain.Conformity;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AnswerUpsertRequest(
        @NotNull(message = "inspectionItemId is required") UUID inspectionItemId,
        String valueText,
        BigDecimal valueNumber,
        Boolean valueBoolean,
        LocalDate valueDate,
        String valueJson,
        String observation,
        Conformity conformity
) {
}
