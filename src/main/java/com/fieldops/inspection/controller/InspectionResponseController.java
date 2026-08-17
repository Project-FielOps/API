package com.fieldops.inspection.controller;

import com.fieldops.auth.security.FieldOpsUserDetails;
import com.fieldops.inspection.application.InspectionService;
import com.fieldops.inspection.domain.InspectionResponse;
import com.fieldops.inspection.dto.AnswerResponse;
import com.fieldops.inspection.dto.AnswerUpsertRequest;
import com.fieldops.inspection.dto.BatchAnswerRequest;
import com.fieldops.inspection.mapper.InspectionMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspections/{inspectionId}/responses")
@RequiredArgsConstructor
public class InspectionResponseController {

    private final InspectionService inspectionService;
    private final InspectionMapper inspectionMapper;

    @GetMapping
    public List<AnswerResponse> list(@PathVariable UUID inspectionId) {
        return inspectionService.getAnswersByItemId(inspectionId).values().stream()
                .map(inspectionMapper::toAnswerResponse).toList();
    }

    @PutMapping("/{inspectionItemId}")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    public AnswerResponse upsert(@PathVariable UUID inspectionId, @PathVariable UUID inspectionItemId,
                                  @Valid @RequestBody AnswerUpsertRequest request,
                                  @AuthenticationPrincipal FieldOpsUserDetails principal) {
        AnswerUpsertRequest withItem = new AnswerUpsertRequest(inspectionItemId, request.valueText(), request.valueNumber(),
                request.valueBoolean(), request.valueDate(), request.valueJson(), request.observation(), request.conformity());
        InspectionResponse response = inspectionService.upsertAnswer(inspectionId, withItem, principal.getUser());
        return inspectionMapper.toAnswerResponse(response);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    public List<AnswerResponse> batchUpsert(@PathVariable UUID inspectionId, @Valid @RequestBody BatchAnswerRequest request,
                                             @AuthenticationPrincipal FieldOpsUserDetails principal) {
        return request.answers().stream()
                .map(a -> inspectionService.upsertAnswer(inspectionId, a, principal.getUser()))
                .map(inspectionMapper::toAnswerResponse)
                .toList();
    }
}
