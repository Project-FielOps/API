package com.fieldops.template.controller;

import com.fieldops.auth.security.FieldOpsUserDetails;
import com.fieldops.shared.dto.PageResponse;
import com.fieldops.template.application.TemplateService;
import com.fieldops.template.domain.InspectionTemplate;
import com.fieldops.template.domain.InspectionTemplateVersion;
import com.fieldops.template.domain.TemplateItem;
import com.fieldops.template.domain.TemplateSection;
import com.fieldops.template.domain.TemplateStatus;
import com.fieldops.template.dto.*;
import com.fieldops.template.mapper.TemplateMapper;
import com.fieldops.template.repository.InspectionTemplateVersionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspection-templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final TemplateMapper templateMapper;
    private final InspectionTemplateVersionRepository versionRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<TemplateResponse> create(@Valid @RequestBody CreateTemplateRequest request,
                                                    @AuthenticationPrincipal FieldOpsUserDetails principal) {
        InspectionTemplate created = templateService.create(request, principal.getUser());
        return ResponseEntity.created(URI.create("/api/v1/inspection-templates/" + created.getId()))
                .body(templateMapper.toResponse(created, true));
    }

    @GetMapping
    public PageResponse<TemplateResponse> list(@RequestParam(required = false) TemplateStatus status,
                                                @RequestParam(required = false) String category,
                                                Pageable pageable) {
        return PageResponse.of(templateService.list(status, category, pageable)
                .map(t -> templateMapper.toResponse(t, versionRepository.findByTemplateIdAndPublishedAtIsNull(t.getId()).isPresent())));
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return templateService.listCategories();
    }

    @GetMapping("/{id}")
    public TemplateResponse get(@PathVariable UUID id) {
        InspectionTemplate t = templateService.getById(id);
        return templateMapper.toResponse(t, versionRepository.findByTemplateIdAndPublishedAtIsNull(id).isPresent());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public TemplateResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTemplateRequest request) {
        InspectionTemplate t = templateService.update(id, request);
        return templateMapper.toResponse(t, versionRepository.findByTemplateIdAndPublishedAtIsNull(id).isPresent());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public TemplateResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateTemplateStatusRequest request) {
        InspectionTemplate t = templateService.updateStatus(id, request.status());
        return templateMapper.toResponse(t, versionRepository.findByTemplateIdAndPublishedAtIsNull(id).isPresent());
    }

    @GetMapping("/{id}/draft")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public TemplateVersionResponse getDraft(@PathVariable UUID id) {
        return toVersionResponse(templateService.getOrCreateDraft(id));
    }

    @PostMapping("/{id}/sections")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<TemplateSectionResponse> addSection(@PathVariable UUID id, @Valid @RequestBody SectionUpsertRequest request) {
        TemplateSection section = templateService.addSection(id, request);
        return ResponseEntity.status(201).body(templateMapper.toResponse(section, List.of()));
    }

    @PutMapping("/{id}/sections/{sectionId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public TemplateSectionResponse updateSection(@PathVariable UUID id, @PathVariable UUID sectionId,
                                                  @Valid @RequestBody SectionUpsertRequest request) {
        TemplateSection section = templateService.updateSection(id, sectionId, request);
        return templateMapper.toResponse(section, templateService.listItems(section.getId()));
    }

    @DeleteMapping("/{id}/sections/{sectionId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<Void> deleteSection(@PathVariable UUID id, @PathVariable UUID sectionId) {
        templateService.deleteSection(id, sectionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/sections/{sectionId}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<TemplateItemResponse> addItem(@PathVariable UUID id, @PathVariable UUID sectionId,
                                                         @Valid @RequestBody ItemUpsertRequest request) {
        TemplateItem item = templateService.addItem(id, sectionId, request);
        return ResponseEntity.status(201).body(templateMapper.toResponse(item));
    }

    @PutMapping("/{id}/sections/{sectionId}/items/{itemId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public TemplateItemResponse updateItem(@PathVariable UUID id, @PathVariable UUID sectionId, @PathVariable UUID itemId,
                                            @Valid @RequestBody ItemUpsertRequest request) {
        return templateMapper.toResponse(templateService.updateItem(id, sectionId, itemId, request));
    }

    @DeleteMapping("/{id}/sections/{sectionId}/items/{itemId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<Void> deleteItem(@PathVariable UUID id, @PathVariable UUID sectionId, @PathVariable UUID itemId) {
        templateService.deleteItem(id, sectionId, itemId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public TemplateVersionResponse publish(@PathVariable UUID id, @AuthenticationPrincipal FieldOpsUserDetails principal) {
        return toVersionResponse(templateService.publish(id, principal.getUser()));
    }

    @GetMapping("/{id}/versions")
    public List<TemplateVersionResponse> listVersions(@PathVariable UUID id) {
        return templateService.listPublishedVersions(id).stream().map(this::toVersionResponse).toList();
    }

    @GetMapping("/{id}/versions/{versionNumber}")
    public TemplateVersionResponse getVersion(@PathVariable UUID id, @PathVariable int versionNumber) {
        return toVersionResponse(templateService.getPublishedVersion(id, versionNumber));
    }

    private TemplateVersionResponse toVersionResponse(InspectionTemplateVersion version) {
        List<TemplateSectionResponse> sections = templateService.listSections(version.getId()).stream()
                .map(s -> templateMapper.toResponse(s, templateService.listItems(s.getId())))
                .toList();
        return templateMapper.toResponse(version, sections);
    }
}
