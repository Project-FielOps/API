package com.fieldops.template.application;

import com.fieldops.audit.application.AuditService;
import com.fieldops.shared.exception.BusinessRuleException;
import com.fieldops.shared.exception.NotFoundException;
import com.fieldops.template.domain.*;
import com.fieldops.template.dto.CreateTemplateRequest;
import com.fieldops.template.dto.ItemUpsertRequest;
import com.fieldops.template.dto.SectionUpsertRequest;
import com.fieldops.template.dto.UpdateTemplateRequest;
import com.fieldops.template.repository.InspectionTemplateRepository;
import com.fieldops.template.repository.InspectionTemplateVersionRepository;
import com.fieldops.template.repository.TemplateItemRepository;
import com.fieldops.template.repository.TemplateSectionRepository;
import com.fieldops.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TemplateService {

    private final InspectionTemplateRepository templateRepository;
    private final InspectionTemplateVersionRepository versionRepository;
    private final TemplateSectionRepository sectionRepository;
    private final TemplateItemRepository itemRepository;
    private final AuditService auditService;

    public InspectionTemplate create(CreateTemplateRequest request, User creator) {
        InspectionTemplate template = new InspectionTemplate();
        template.setTitle(request.title());
        template.setDescription(request.description());
        template.setCategory(request.category());
        template.setStatus(TemplateStatus.DRAFT);
        template.setCurrentVersion(0);
        template.setCreatedBy(creator);
        template = templateRepository.save(template);

        InspectionTemplateVersion draft = new InspectionTemplateVersion();
        draft.setTemplate(template);
        draft.setVersionNumber(1);
        draft.setTitleSnapshot(request.title());
        draft.setDescriptionSnapshot(request.description());
        draft.setActiveForNewInspections(false);
        versionRepository.save(draft);

        auditService.record(null, creator.getId(), "TEMPLATE_CREATED", "InspectionTemplate", template.getId());
        return template;
    }

    @Transactional(readOnly = true)
    public InspectionTemplate getById(UUID id) {
        return templateRepository.findById(id).orElseThrow(() -> NotFoundException.of("InspectionTemplate", id));
    }

    @Transactional(readOnly = true)
    public Page<InspectionTemplate> list(TemplateStatus status, String category, Pageable pageable) {
        if (status != null) {
            return templateRepository.findByStatus(status, pageable);
        }
        if (category != null && !category.isBlank()) {
            return templateRepository.findByCategoryIgnoreCase(category, pageable);
        }
        return templateRepository.findAll(pageable);
    }

    public InspectionTemplate update(UUID id, UpdateTemplateRequest request) {
        InspectionTemplate template = getById(id);
        template.setTitle(request.title());
        template.setDescription(request.description());
        template.setCategory(request.category());
        return templateRepository.save(template);
    }

    public InspectionTemplate updateStatus(UUID id, TemplateStatus status) {
        InspectionTemplate template = getById(id);
        if (status == TemplateStatus.ACTIVE && template.getCurrentVersion() == 0) {
            throw new BusinessRuleException("TEMPLATE_NOT_PUBLISHED",
                    "Cannot activate a template that has never been published");
        }
        template.setStatus(status);
        return templateRepository.save(template);
    }

    /** Finds the currently-open (unpublished) draft version, creating one from the latest published version if needed. */
    public InspectionTemplateVersion getOrCreateDraft(UUID templateId) {
        InspectionTemplate template = getById(templateId);
        return versionRepository.findByTemplateIdAndPublishedAtIsNull(templateId)
                .orElseGet(() -> createNewDraftFromLatestPublished(template));
    }

    private InspectionTemplateVersion createNewDraftFromLatestPublished(InspectionTemplate template) {
        InspectionTemplateVersion draft = new InspectionTemplateVersion();
        draft.setTemplate(template);
        draft.setVersionNumber(template.getCurrentVersion() + 1);
        draft.setTitleSnapshot(template.getTitle());
        draft.setDescriptionSnapshot(template.getDescription());
        draft.setActiveForNewInspections(false);
        draft = versionRepository.save(draft);

        InspectionTemplateVersion latestPublished = versionRepository
                .findTopByTemplateIdAndPublishedAtIsNotNullOrderByVersionNumberDesc(template.getId()).orElse(null);
        if (latestPublished != null) {
            for (TemplateSection section : sectionRepository.findByTemplateVersionIdOrderByDisplayOrderAsc(latestPublished.getId())) {
                TemplateSection newSection = new TemplateSection();
                newSection.setTemplateVersion(draft);
                newSection.setTitle(section.getTitle());
                newSection.setDescription(section.getDescription());
                newSection.setDisplayOrder(section.getDisplayOrder());
                newSection = sectionRepository.save(newSection);
                for (TemplateItem item : itemRepository.findBySectionIdOrderByDisplayOrderAsc(section.getId())) {
                    TemplateItem newItem = new TemplateItem();
                    newItem.setSection(newSection);
                    newItem.setCode(item.getCode());
                    newItem.setTitle(item.getTitle());
                    newItem.setDescription(item.getDescription());
                    newItem.setResponseType(item.getResponseType());
                    newItem.setRequired(item.isRequired());
                    newItem.setObservationRequiredOnFailure(item.isObservationRequiredOnFailure());
                    newItem.setEvidenceRequiredOnFailure(item.isEvidenceRequiredOnFailure());
                    newItem.setOptionsJson(item.getOptionsJson());
                    newItem.setDisplayOrder(item.getDisplayOrder());
                    itemRepository.save(newItem);
                }
            }
        }
        return draft;
    }

    @Transactional(readOnly = true)
    public InspectionTemplateVersion getVersion(UUID versionId) {
        return versionRepository.findById(versionId).orElseThrow(() -> NotFoundException.of("InspectionTemplateVersion", versionId));
    }

    @Transactional(readOnly = true)
    public InspectionTemplateVersion getPublishedVersion(UUID templateId, int versionNumber) {
        InspectionTemplateVersion v = versionRepository.findByTemplateIdAndVersionNumber(templateId, versionNumber)
                .orElseThrow(() -> NotFoundException.of("InspectionTemplateVersion", versionNumber));
        if (!v.isPublished()) {
            throw new NotFoundException("InspectionTemplateVersion not published: " + versionNumber);
        }
        return v;
    }

    @Transactional(readOnly = true)
    public List<InspectionTemplateVersion> listPublishedVersions(UUID templateId) {
        getById(templateId);
        return versionRepository.findByTemplateIdAndPublishedAtIsNotNullOrderByVersionNumberDesc(templateId);
    }

    @Transactional(readOnly = true)
    public List<TemplateSection> listSections(UUID versionId) {
        return sectionRepository.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId);
    }

    @Transactional(readOnly = true)
    public List<TemplateItem> listItems(UUID sectionId) {
        return itemRepository.findBySectionIdOrderByDisplayOrderAsc(sectionId);
    }

    public TemplateSection addSection(UUID templateId, SectionUpsertRequest request) {
        InspectionTemplateVersion draft = getOrCreateDraft(templateId);
        TemplateSection section = new TemplateSection();
        section.setTemplateVersion(draft);
        section.setTitle(request.title());
        section.setDescription(request.description());
        section.setDisplayOrder(request.displayOrder());
        return sectionRepository.save(section);
    }

    public TemplateSection updateSection(UUID templateId, UUID sectionId, SectionUpsertRequest request) {
        TemplateSection section = requireEditableSection(templateId, sectionId);
        section.setTitle(request.title());
        section.setDescription(request.description());
        section.setDisplayOrder(request.displayOrder());
        return sectionRepository.save(section);
    }

    public void deleteSection(UUID templateId, UUID sectionId) {
        TemplateSection section = requireEditableSection(templateId, sectionId);
        itemRepository.deleteAll(itemRepository.findBySectionIdOrderByDisplayOrderAsc(section.getId()));
        sectionRepository.delete(section);
    }

    public TemplateItem addItem(UUID templateId, UUID sectionId, ItemUpsertRequest request) {
        TemplateSection section = requireEditableSection(templateId, sectionId);
        TemplateItem item = new TemplateItem();
        item.setSection(section);
        applyItem(item, request);
        return itemRepository.save(item);
    }

    public TemplateItem updateItem(UUID templateId, UUID sectionId, UUID itemId, ItemUpsertRequest request) {
        requireEditableSection(templateId, sectionId);
        TemplateItem item = itemRepository.findById(itemId).orElseThrow(() -> NotFoundException.of("TemplateItem", itemId));
        applyItem(item, request);
        return itemRepository.save(item);
    }

    public void deleteItem(UUID templateId, UUID sectionId, UUID itemId) {
        requireEditableSection(templateId, sectionId);
        TemplateItem item = itemRepository.findById(itemId).orElseThrow(() -> NotFoundException.of("TemplateItem", itemId));
        itemRepository.delete(item);
    }

    private void applyItem(TemplateItem item, ItemUpsertRequest request) {
        item.setCode(request.code());
        item.setTitle(request.title());
        item.setDescription(request.description());
        item.setResponseType(request.responseType());
        item.setRequired(request.required());
        item.setObservationRequiredOnFailure(request.observationRequiredOnFailure());
        item.setEvidenceRequiredOnFailure(request.evidenceRequiredOnFailure());
        item.setOptionsJson(request.optionsJson());
        item.setDisplayOrder(request.displayOrder());
    }

    private TemplateSection requireEditableSection(UUID templateId, UUID sectionId) {
        TemplateSection section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> NotFoundException.of("TemplateSection", sectionId));
        if (!section.getTemplateVersion().getTemplate().getId().equals(templateId)) {
            throw NotFoundException.of("TemplateSection", sectionId);
        }
        if (section.getTemplateVersion().isPublished()) {
            throw new BusinessRuleException("TEMPLATE_VERSION_IMMUTABLE",
                    "This section belongs to a published (immutable) version. Edit the current draft instead.");
        }
        return section;
    }

    public InspectionTemplateVersion publish(UUID templateId, User publisher) {
        InspectionTemplate template = getById(templateId);
        InspectionTemplateVersion draft = versionRepository.findByTemplateIdAndPublishedAtIsNull(templateId)
                .orElseThrow(() -> new BusinessRuleException("NO_OPEN_DRAFT", "There is no open draft to publish"));

        List<TemplateSection> sections = sectionRepository.findByTemplateVersionIdOrderByDisplayOrderAsc(draft.getId());
        if (sections.isEmpty()) {
            throw new BusinessRuleException("EMPTY_TEMPLATE", "A template must have at least one section before publishing");
        }
        boolean anyItems = sections.stream()
                .anyMatch(s -> !itemRepository.findBySectionIdOrderByDisplayOrderAsc(s.getId()).isEmpty());
        if (!anyItems) {
            throw new BusinessRuleException("EMPTY_TEMPLATE", "A template must have at least one checklist item before publishing");
        }

        // Only the latest published version stays selectable for new inspections.
        versionRepository.findByTemplateIdAndPublishedAtIsNotNullOrderByVersionNumberDesc(templateId)
                .forEach(v -> {
                    v.setActiveForNewInspections(false);
                    versionRepository.save(v);
                });

        draft.setTitleSnapshot(template.getTitle());
        draft.setDescriptionSnapshot(template.getDescription());
        draft.setPublishedBy(publisher);
        draft.setPublishedAt(java.time.Instant.now());
        draft.setActiveForNewInspections(true);
        draft = versionRepository.save(draft);

        template.setCurrentVersion(draft.getVersionNumber());
        template.setStatus(TemplateStatus.ACTIVE);
        templateRepository.save(template);

        auditService.record(null, publisher.getId(), "TEMPLATE_VERSION_PUBLISHED", "InspectionTemplateVersion", draft.getId());
        return draft;
    }
}
