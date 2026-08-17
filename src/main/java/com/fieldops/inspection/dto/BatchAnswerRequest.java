package com.fieldops.inspection.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchAnswerRequest(
        @NotEmpty(message = "answers must not be empty") @Valid List<AnswerUpsertRequest> answers
) {
}
