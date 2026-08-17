package com.fieldops.inspection.dto;

import com.fieldops.template.domain.ResponseType;

import java.util.UUID;

public record ItemSnapshotResponse(
        UUID id,
        String sectionTitle,
        String sectionDescription,
        Integer sectionOrder,
        String itemCode,
        String itemTitle,
        String itemDescription,
        ResponseType responseType,
        boolean required,
        String rulesJson,
        String optionsJson,
        Integer itemOrder,
        AnswerResponse answer
) {
}
