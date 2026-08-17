package com.fieldops.inspection.mapper;

import com.fieldops.inspection.domain.Inspection;
import com.fieldops.inspection.domain.InspectionItemSnapshot;
import com.fieldops.inspection.domain.InspectionResponse;
import com.fieldops.inspection.dto.AnswerResponse;
import com.fieldops.inspection.dto.InspectionDetailResponse;
import com.fieldops.inspection.dto.InspectionSummaryResponse;
import com.fieldops.inspection.dto.ItemSnapshotResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class InspectionMapper {

    public InspectionSummaryResponse toSummary(Inspection i) {
        return new InspectionSummaryResponse(i.getId(), i.getTitle(), i.getClient().getId(), i.getClient().getName(),
                i.getSite().getId(), i.getSite().getName(),
                Optional.ofNullable(i.getEquipment()).map(e -> e.getId()).orElse(null),
                Optional.ofNullable(i.getEquipment()).map(e -> e.getName()).orElse(null),
                i.getTechnician().getId(), i.getTechnician().getName(),
                i.getSupervisor().getId(), i.getSupervisor().getName(),
                i.getPriority(), i.getStatus(), i.getScheduledFor(),
                i.getTemplateVersion().getVersionNumber(), i.getTemplateVersion().getTitleSnapshot());
    }

    public AnswerResponse toAnswerResponse(InspectionResponse r) {
        return new AnswerResponse(r.getId(), r.getInspectionItem().getId(), r.getValueText(), r.getValueNumber(),
                r.getValueBoolean(), r.getValueDate(), r.getValueJson(), r.getObservation(), r.getConformity(),
                Optional.ofNullable(r.getAnsweredBy()).map(u -> u.getId()).orElse(null), r.getServerReceivedAt(), r.getVersion());
    }

    public ItemSnapshotResponse toItemResponse(InspectionItemSnapshot s, Map<UUID, InspectionResponse> answersByItemId) {
        InspectionResponse answer = answersByItemId.get(s.getId());
        return new ItemSnapshotResponse(s.getId(), s.getSectionTitle(), s.getSectionDescription(), s.getSectionOrder(),
                s.getItemCode(), s.getItemTitle(), s.getItemDescription(), s.getResponseType(), s.isRequired(),
                s.getRulesJson(), s.getOptionsJson(), s.getItemOrder(), answer == null ? null : toAnswerResponse(answer));
    }

    public InspectionDetailResponse toDetail(Inspection i, List<InspectionItemSnapshot> snapshots,
                                              Map<UUID, InspectionResponse> answersByItemId) {
        List<ItemSnapshotResponse> items = snapshots.stream().map(s -> toItemResponse(s, answersByItemId)).toList();
        return new InspectionDetailResponse(i.getId(), i.getTemplateVersion().getId(), i.getTemplateVersion().getTemplate().getId(),
                i.getTemplateVersion().getVersionNumber(), i.getTemplateVersion().getTitleSnapshot(),
                i.getClient().getId(), i.getClient().getName(), i.getSite().getId(), i.getSite().getName(),
                Optional.ofNullable(i.getEquipment()).map(e -> e.getId()).orElse(null),
                Optional.ofNullable(i.getEquipment()).map(e -> e.getName()).orElse(null),
                i.getTechnician().getId(), i.getTechnician().getName(),
                i.getSupervisor().getId(), i.getSupervisor().getName(),
                i.getCreatedBy().getId(), i.getTitle(), i.getInstructions(), i.getPriority(), i.getStatus(),
                i.getScheduledFor(), i.getStartedAtServer(), i.getSubmittedAtServer(), i.getApprovedAt(),
                i.getCanceledAt(), Optional.ofNullable(i.getCanceledBy()).map(u -> u.getId()).orElse(null),
                i.getCanceledReason(), i.getCreatedAt(), i.getUpdatedAt(), i.getVersion(), items);
    }
}
