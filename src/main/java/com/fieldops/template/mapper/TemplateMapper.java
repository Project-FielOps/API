package com.fieldops.template.mapper;

import com.fieldops.template.domain.InspectionTemplate;
import com.fieldops.template.domain.InspectionTemplateVersion;
import com.fieldops.template.domain.TemplateItem;
import com.fieldops.template.domain.TemplateSection;
import com.fieldops.template.dto.TemplateItemResponse;
import com.fieldops.template.dto.TemplateResponse;
import com.fieldops.template.dto.TemplateSectionResponse;
import com.fieldops.template.dto.TemplateVersionResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TemplateMapper {

    public TemplateResponse toResponse(InspectionTemplate t, boolean hasOpenDraft) {
        return new TemplateResponse(t.getId(), t.getTitle(), t.getDescription(), t.getCategory(), t.getStatus(),
                t.getCurrentVersion(), hasOpenDraft, t.getCreatedBy().getId(), t.getCreatedAt(), t.getUpdatedAt(), t.getVersion());
    }

    public TemplateItemResponse toResponse(TemplateItem i) {
        return new TemplateItemResponse(i.getId(), i.getCode(), i.getTitle(), i.getDescription(), i.getResponseType(),
                i.isRequired(), i.isObservationRequiredOnFailure(), i.isEvidenceRequiredOnFailure(), i.getOptionsJson(), i.getDisplayOrder());
    }

    public TemplateSectionResponse toResponse(TemplateSection s, List<TemplateItem> items) {
        return new TemplateSectionResponse(s.getId(), s.getTitle(), s.getDescription(), s.getDisplayOrder(),
                items.stream().map(this::toResponse).toList());
    }

    public TemplateVersionResponse toResponse(InspectionTemplateVersion v, List<TemplateSectionResponse> sections) {
        return new TemplateVersionResponse(v.getId(), v.getTemplate().getId(), v.getVersionNumber(),
                v.getTitleSnapshot(), v.getDescriptionSnapshot(),
                Optional.ofNullable(v.getPublishedBy()).map(u -> u.getId()).orElse(null),
                v.getPublishedAt(), v.isActiveForNewInspections(), v.isPublished(), sections);
    }
}
