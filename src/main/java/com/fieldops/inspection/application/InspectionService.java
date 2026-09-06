package com.fieldops.inspection.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.audit.application.AuditService;
import com.fieldops.client.application.ClientService;
import com.fieldops.client.domain.Client;
import com.fieldops.equipment.application.EquipmentService;
import com.fieldops.equipment.domain.Equipment;
import com.fieldops.inspection.domain.*;
import com.fieldops.inspection.dto.AnswerUpsertRequest;
import com.fieldops.inspection.dto.CreateInspectionRequest;
import com.fieldops.inspection.repository.InspectionItemSnapshotRepository;
import com.fieldops.inspection.repository.InspectionRepository;
import com.fieldops.inspection.repository.InspectionResponseRepository;
import com.fieldops.evidence.repository.EvidenceRepository;
import com.fieldops.review.domain.NonConformitySeverity;
import com.fieldops.review.repository.NonConformityRepository;
import com.fieldops.shared.exception.BusinessRuleException;
import com.fieldops.shared.exception.ForbiddenOperationException;
import com.fieldops.shared.exception.NotFoundException;
import com.fieldops.site.application.SiteService;
import com.fieldops.site.domain.InspectionSite;
import com.fieldops.template.application.TemplateService;
import com.fieldops.template.domain.InspectionTemplateVersion;
import com.fieldops.template.domain.TemplateItem;
import com.fieldops.template.domain.TemplateSection;
import com.fieldops.template.domain.ResponseType;
import com.fieldops.user.application.UserService;
import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.User;
import com.fieldops.user.domain.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class InspectionService {

    private static final Set<InspectionStatus> EDITABLE_STATUSES = Set.of(InspectionStatus.IN_PROGRESS);

    private final InspectionRepository inspectionRepository;
    private final InspectionItemSnapshotRepository snapshotRepository;
    private final InspectionResponseRepository responseRepository;
    private final EvidenceRepository evidenceRepository;
    private final ClientService clientService;
    private final SiteService siteService;
    private final EquipmentService equipmentService;
    private final UserService userService;
    private final TemplateService templateService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final NonConformityRepository nonConformityRepository;

    public Inspection create(CreateInspectionRequest request, User creator) {
        Client client = clientService.getById(request.clientId());
        if (!client.isActive()) {
            throw new BusinessRuleException("CLIENT_INACTIVE", "Cannot schedule an inspection for an inactive client");
        }
        InspectionSite site = siteService.getById(request.siteId());
        if (!site.isActive()) {
            throw new BusinessRuleException("SITE_INACTIVE", "Cannot schedule an inspection for an inactive site");
        }
        if (!site.getClient().getId().equals(client.getId())) {
            throw new BusinessRuleException("SITE_CLIENT_MISMATCH", "The site does not belong to the given client");
        }
        Equipment equipment = null;
        if (request.equipmentId() != null) {
            equipment = equipmentService.getById(request.equipmentId());
            if (!equipment.isUsable()) {
                throw new BusinessRuleException("EQUIPMENT_INACTIVE", "Inactive equipment cannot be used in new inspections");
            }
            if (!equipment.getSite().getId().equals(site.getId())) {
                throw new BusinessRuleException("EQUIPMENT_SITE_MISMATCH", "The equipment does not belong to the given site");
            }
        }
        User technician = userService.getById(request.technicianId());
        if (technician.getRole() != Role.TECHNICIAN || technician.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessRuleException("INVALID_TECHNICIAN", "technicianId must reference an active user with role TECHNICIAN");
        }
        User supervisor = userService.getById(request.supervisorId());
        if ((supervisor.getRole() != Role.SUPERVISOR && supervisor.getRole() != Role.ADMIN) || supervisor.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessRuleException("INVALID_SUPERVISOR", "supervisorId must reference an active user with role SUPERVISOR or ADMIN");
        }

        InspectionTemplateVersion templateVersion = request.templateVersionNumber() != null
                ? templateService.getPublishedVersion(request.templateId(), request.templateVersionNumber())
                : templateService.listPublishedVersions(request.templateId()).stream()
                        .filter(InspectionTemplateVersion::isActiveForNewInspections)
                        .findFirst()
                        .orElseThrow(() -> new BusinessRuleException("NO_PUBLISHED_VERSION",
                                "The template has no published version available for new inspections"));
        if (!templateVersion.isPublished()) {
            throw new BusinessRuleException("TEMPLATE_VERSION_NOT_PUBLISHED", "Inspections must reference a published template version");
        }
        if (!templateVersion.isActiveForNewInspections()) {
            throw new BusinessRuleException("TEMPLATE_VERSION_NOT_ACTIVE",
                    "The selected template version is not active for new inspections");
        }

        Inspection inspection = new Inspection();
        inspection.setTemplateVersion(templateVersion);
        inspection.setClient(client);
        inspection.setSite(site);
        inspection.setEquipment(equipment);
        inspection.setTechnician(technician);
        inspection.setSupervisor(supervisor);
        inspection.setCreatedBy(creator);
        inspection.setTitle(request.title());
        inspection.setInstructions(request.instructions());
        inspection.setPriority(request.priority() == null ? Priority.MEDIUM : request.priority());
        inspection.setStatus(InspectionStatus.ASSIGNED);
        inspection.setScheduledFor(request.scheduledFor());
        inspection = inspectionRepository.save(inspection);

        copySnapshot(inspection, templateVersion);

        auditService.record(inspection.getId(), creator.getId(), "INSPECTION_CREATED", "Inspection", inspection.getId());
        auditService.record(inspection.getId(), creator.getId(), "INSPECTION_ASSIGNED", "Inspection", inspection.getId());
        return inspection;
    }

    private void copySnapshot(Inspection inspection, InspectionTemplateVersion version) {
        List<TemplateSection> sections = templateService.listSections(version.getId());
        for (TemplateSection section : sections) {
            for (TemplateItem item : templateService.listItems(section.getId())) {
                InspectionItemSnapshot snapshot = new InspectionItemSnapshot();
                snapshot.setInspection(inspection);
                snapshot.setSourceTemplateItemId(item.getId());
                snapshot.setSectionTitle(section.getTitle());
                snapshot.setSectionDescription(section.getDescription());
                snapshot.setSectionOrder(section.getDisplayOrder());
                snapshot.setItemCode(item.getCode());
                snapshot.setItemTitle(item.getTitle());
                snapshot.setItemDescription(item.getDescription());
                snapshot.setResponseType(item.getResponseType());
                snapshot.setRequired(item.isRequired());
                snapshot.setOptionsJson(item.getOptionsJson());
                snapshot.setItemOrder(item.getDisplayOrder());
                snapshot.setRulesJson(writeRules(item.isObservationRequiredOnFailure(), item.isEvidenceRequiredOnFailure()));
                snapshotRepository.save(snapshot);
            }
        }
    }

    private String writeRules(boolean observationRequired, boolean evidenceRequired) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "observationRequiredOnFailure", observationRequired,
                    "evidenceRequiredOnFailure", evidenceRequired));
        } catch (Exception e) {
            return "{}";
        }
    }

    @Transactional(readOnly = true)
    public Inspection getById(UUID id) {
        return inspectionRepository.findById(id).orElseThrow(() -> NotFoundException.of("Inspection", id));
    }

    @Transactional(readOnly = true)
    public Inspection getAuthorized(UUID id, User actor) {
        Inspection inspection = getById(id);
        requireReadAccess(inspection, actor);
        return inspection;
    }

    @Transactional(readOnly = true)
    public List<InspectionItemSnapshot> getSnapshots(UUID inspectionId) {
        return snapshotRepository.findByInspectionIdOrderBySectionOrderAscItemOrderAsc(inspectionId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, InspectionResponse> getAnswersByItemId(UUID inspectionId) {
        Map<UUID, InspectionResponse> map = new HashMap<>();
        for (InspectionResponse r : responseRepository.findByInspectionId(inspectionId)) {
            map.put(r.getInspectionItem().getId(), r);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public Page<Inspection> list(UUID technicianId, UUID supervisorId, UUID clientId, UUID siteId,
                                  InspectionStatus status, Pageable pageable) {
        if (technicianId != null && status != null) return inspectionRepository.findByTechnicianIdAndStatus(technicianId, status, pageable);
        if (technicianId != null) return inspectionRepository.findByTechnicianId(technicianId, pageable);
        if (supervisorId != null && status != null) return inspectionRepository.findBySupervisorIdAndStatus(supervisorId, status, pageable);
        if (supervisorId != null) return inspectionRepository.findBySupervisorId(supervisorId, pageable);
        if (clientId != null) return inspectionRepository.findByClientId(clientId, pageable);
        if (siteId != null) return inspectionRepository.findBySiteId(siteId, pageable);
        if (status != null) return inspectionRepository.findByStatus(status, pageable);
        return inspectionRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Inspection> listForActor(User actor, UUID technicianId, UUID supervisorId, UUID clientId,
                                         UUID siteId, InspectionStatus status, Priority priority,
                                         Instant scheduledFrom, Instant scheduledTo, Pageable pageable) {
        if (actor.getRole() == Role.TECHNICIAN) {
            technicianId = actor.getId();
            supervisorId = null;
            clientId = null;
            siteId = null;
        }
        if (actor.getRole() == Role.SUPERVISOR) {
            technicianId = null;
            supervisorId = actor.getId();
            clientId = null;
            siteId = null;
        }
        return inspectionRepository.findFiltered(technicianId, supervisorId, clientId, siteId, status, priority,
                scheduledFrom, scheduledTo, pageable);
    }

    public Inspection assign(UUID id, UUID technicianId, UUID supervisorId, User actor) {
        Inspection inspection = getById(id);
        requireStatus(inspection, "ASSIGN", InspectionStatus.ASSIGNED);
        User technician = userService.getById(technicianId);
        if (technician.getRole() != Role.TECHNICIAN || technician.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessRuleException("INVALID_TECHNICIAN", "technicianId must reference an active user with role TECHNICIAN");
        }
        User supervisor = userService.getById(supervisorId);
        if ((supervisor.getRole() != Role.SUPERVISOR && supervisor.getRole() != Role.ADMIN) || supervisor.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessRuleException("INVALID_SUPERVISOR", "supervisorId must reference an active user with role SUPERVISOR or ADMIN");
        }
        inspection.setTechnician(technician);
        inspection.setSupervisor(supervisor);
        inspection = inspectionRepository.save(inspection);
        auditService.record(inspection.getId(), actor.getId(), "INSPECTION_ASSIGNED", "Inspection", inspection.getId());
        return inspection;
    }

    public Inspection cancel(UUID id, String reason, User actor) {
        Inspection inspection = getById(id);
        if (!Set.of(InspectionStatus.ASSIGNED, InspectionStatus.IN_PROGRESS, InspectionStatus.REJECTED).contains(inspection.getStatus())) {
            throw new BusinessRuleException("INVALID_TRANSITION", "Inspection cannot be canceled from status " + inspection.getStatus());
        }
        inspection.setStatus(InspectionStatus.CANCELED);
        inspection.setCanceledAt(Instant.now());
        inspection.setCanceledBy(actor);
        inspection.setCanceledReason(reason);
        inspection = inspectionRepository.save(inspection);
        auditService.record(inspection.getId(), actor.getId(), "INSPECTION_CANCELED", "Inspection", inspection.getId());
        return inspection;
    }

    public Inspection start(UUID id, User actor) {
        Inspection inspection = getById(id);
        requireStatus(inspection, "START", InspectionStatus.ASSIGNED);
        requireTechnician(inspection, actor);
        inspection.setStatus(InspectionStatus.IN_PROGRESS);
        inspection.setStartedAtServer(Instant.now());
        inspection = inspectionRepository.save(inspection);
        auditService.record(inspection.getId(), actor.getId(), "INSPECTION_STARTED", "Inspection", inspection.getId());
        return inspection;
    }

    public Inspection submit(UUID id, User actor) {
        Inspection inspection = getById(id);
        if (!EDITABLE_STATUSES.contains(inspection.getStatus())) {
            throw new BusinessRuleException("INVALID_TRANSITION", "Inspection cannot be submitted from status " + inspection.getStatus());
        }
        requireTechnician(inspection, actor);
        validateRequiredItemsAnswered(inspection);
        inspection.setStatus(InspectionStatus.SUBMITTED);
        inspection.setSubmittedAtServer(Instant.now());
        inspection = inspectionRepository.save(inspection);
        auditService.record(inspection.getId(), actor.getId(), "INSPECTION_SUBMITTED", "Inspection", inspection.getId());
        return inspection;
    }

    public Inspection reopenCorrection(UUID id, User actor) {
        Inspection inspection = getById(id);
        requireStatus(inspection, "REOPEN_CORRECTION", InspectionStatus.REJECTED);
        requireTechnician(inspection, actor);
        inspection.setStatus(InspectionStatus.IN_PROGRESS);
        inspection = inspectionRepository.save(inspection);
        auditService.record(inspection.getId(), actor.getId(), "INSPECTION_REOPENED_FOR_CORRECTION", "Inspection", inspection.getId());
        return inspection;
    }

    private void validateRequiredItemsAnswered(Inspection inspection) {
        List<InspectionItemSnapshot> snapshots = getSnapshots(inspection.getId());
        Map<UUID, InspectionResponse> answers = getAnswersByItemId(inspection.getId());
        for (InspectionItemSnapshot snapshot : snapshots) {
            InspectionResponse answer = answers.get(snapshot.getId());
            if (snapshot.isRequired() && !isAnswered(answer)) {
                throw new BusinessRuleException("REQUIRED_ITEM_MISSING",
                        "Required item '" + snapshot.getItemTitle() + "' has not been answered");
            }
            if (answer != null && answer.getConformity() == Conformity.NON_CONFORMING) {
                RuleFlags flags = readRules(snapshot.getRulesJson());
                if (flags.observationRequiredOnFailure() && (answer.getObservation() == null || answer.getObservation().isBlank())) {
                    throw new BusinessRuleException("OBSERVATION_REQUIRED",
                            "Item '" + snapshot.getItemTitle() + "' requires an observation because it was answered as non-conforming");
                }
                if (flags.evidenceRequiredOnFailure() && evidenceRepository.findByResponseId(answer.getId()).isEmpty()) {
                    throw new BusinessRuleException("EVIDENCE_REQUIRED",
                            "Item '" + snapshot.getItemTitle() + "' requires evidence because it was answered as non-conforming");
                }
            }
        }
        nonConformityRepository.findByInspectionId(inspection.getId()).stream()
                .filter(nc -> nc.getSeverity() == NonConformitySeverity.CRITICAL)
                .forEach(nc -> {
                    if (nc.getDescription() == null || nc.getDescription().isBlank()) {
                        throw new BusinessRuleException("DESCRIPTION_REQUIRED",
                                "Critical non-conformities require a description");
                    }
                    if (evidenceRepository.findByNonConformityId(nc.getId()).isEmpty()) {
                        throw new BusinessRuleException("EVIDENCE_REQUIRED",
                                "Critical non-conformities require evidence before submission");
                    }
                });
    }

    private boolean isAnswered(InspectionResponse answer) {
        if (answer == null) return false;
        return answer.getConformity() != null || answer.getValueText() != null || answer.getValueNumber() != null
                || answer.getValueBoolean() != null || answer.getValueDate() != null || answer.getValueJson() != null;
    }

    private record RuleFlags(boolean observationRequiredOnFailure, boolean evidenceRequiredOnFailure) {
    }

    private RuleFlags readRules(String json) {
        try {
            Map<?, ?> map = objectMapper.readValue(json == null ? "{}" : json, Map.class);
            return new RuleFlags(Boolean.TRUE.equals(map.get("observationRequiredOnFailure")),
                    Boolean.TRUE.equals(map.get("evidenceRequiredOnFailure")));
        } catch (Exception e) {
            return new RuleFlags(false, false);
        }
    }

    public InspectionResponse upsertAnswer(UUID inspectionId, AnswerUpsertRequest request, User actor) {
        Inspection inspection = getById(inspectionId);
        if (!EDITABLE_STATUSES.contains(inspection.getStatus())) {
            throw new BusinessRuleException("INSPECTION_LOCKED",
                    "Responses can only be recorded while the inspection is IN_PROGRESS (current status: " + inspection.getStatus() + ")");
        }
        requireTechnician(inspection, actor);
        InspectionItemSnapshot snapshot = snapshotRepository.findById(request.inspectionItemId())
                .orElseThrow(() -> NotFoundException.of("InspectionItemSnapshot", request.inspectionItemId()));
        if (!snapshot.getInspection().getId().equals(inspectionId)) {
            throw new BusinessRuleException("ITEM_INSPECTION_MISMATCH", "The item does not belong to this inspection");
        }
        InspectionResponse response = responseRepository.findByInspectionItemId(snapshot.getId()).orElseGet(() -> {
            InspectionResponse r = new InspectionResponse();
            r.setInspection(inspection);
            r.setInspectionItem(snapshot);
            return r;
        });
        boolean isNew = response.getId() == null;
        validateResponseType(snapshot.getResponseType(), request);
        String previousValueJson = isNew ? null : serializeResponse(response);
        response.setValueText(request.valueText());
        response.setValueNumber(request.valueNumber());
        response.setValueBoolean(request.valueBoolean());
        response.setValueDate(request.valueDate());
        response.setValueJson(request.valueJson());
        response.setObservation(request.observation());
        response.setConformity(request.conformity());
        response.setAnsweredBy(actor);
        response.setAnsweredAtDevice(Instant.now());
        response.setServerReceivedAt(Instant.now());
        response = responseRepository.save(response);
        auditService.record(inspection.getId(), actor.getId(), isNew ? "RESPONSE_CREATED" : "RESPONSE_UPDATED",
                "InspectionResponse", response.getId(), previousValueJson, serializeResponse(response), null);
        return response;
    }

    private void validateResponseType(ResponseType responseType, AnswerUpsertRequest request) {
        long populated = java.util.stream.Stream.of(request.valueText(), request.valueNumber(), request.valueBoolean(),
                        request.valueDate(), request.valueJson(), request.conformity())
                .filter(value -> value != null && (!(value instanceof String text) || !text.isBlank()))
                .count();
        boolean valid = switch (responseType) {
            case TEXT_SHORT, TEXT_LONG -> populated == 0 || request.valueText() != null;
            case NUMBER -> populated == 0 || request.valueNumber() != null;
            case BOOLEAN -> populated == 0 || request.valueBoolean() != null;
            case DATE -> populated == 0 || request.valueDate() != null;
            case CONFORMITY -> populated == 0 || request.conformity() != null;
            case SINGLE_CHOICE -> populated == 0 || request.valueJson() != null;
        };
        if (!valid || populated > 1) {
            throw new BusinessRuleException("INVALID_RESPONSE_TYPE",
                    "The answer fields are incompatible with response type " + responseType);
        }
    }

    private String serializeResponse(InspectionResponse response) {
        try {
            Map<String, Object> values = new HashMap<>();
            values.put("valueText", response.getValueText());
            values.put("valueNumber", response.getValueNumber());
            values.put("valueBoolean", response.getValueBoolean());
            values.put("valueDate", response.getValueDate());
            values.put("valueJson", response.getValueJson());
            values.put("observation", response.getObservation());
            values.put("conformity", response.getConformity());
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            return "{}";
        }
    }

    public Inspection beginReview(UUID id, User actor) {
        Inspection inspection = getById(id);
        requireStatus(inspection, "BEGIN_REVIEW", InspectionStatus.SUBMITTED);
        requireSupervisor(inspection, actor);
        inspection.setStatus(InspectionStatus.UNDER_REVIEW);
        inspection = inspectionRepository.save(inspection);
        auditService.record(inspection.getId(), actor.getId(), "REVIEW_STARTED", "Inspection", inspection.getId());
        return inspection;
    }

    public Inspection approve(UUID id, User actor) {
        Inspection inspection = getById(id);
        requireStatus(inspection, "APPROVE", InspectionStatus.UNDER_REVIEW);
        requireSupervisor(inspection, actor);
        inspection.setStatus(InspectionStatus.APPROVED);
        inspection.setApprovedAt(Instant.now());
        inspection = inspectionRepository.save(inspection);
        auditService.record(inspection.getId(), actor.getId(), "INSPECTION_APPROVED", "Inspection", inspection.getId());
        return inspection;
    }

    public Inspection reject(UUID id, User actor) {
        Inspection inspection = getById(id);
        requireStatus(inspection, "REJECT", InspectionStatus.UNDER_REVIEW);
        requireSupervisor(inspection, actor);
        inspection.setStatus(InspectionStatus.REJECTED);
        inspection = inspectionRepository.save(inspection);
        auditService.record(inspection.getId(), actor.getId(), "INSPECTION_REJECTED", "Inspection", inspection.getId());
        return inspection;
    }

    private void requireSupervisor(Inspection inspection, User actor) {
        if (actor.getRole() != Role.SUPERVISOR || !inspection.getSupervisor().getId().equals(actor.getId())) {
            throw new ForbiddenOperationException("Only the assigned supervisor can review this inspection");
        }
    }

    private void requireStatus(Inspection inspection, String action, InspectionStatus expected) {
        if (inspection.getStatus() != expected) {
            throw new BusinessRuleException("INVALID_TRANSITION",
                    "Cannot " + action + " an inspection in status " + inspection.getStatus() + " (expected " + expected + ")");
        }
    }

    private void requireTechnician(Inspection inspection, User actor) {
        if (actor.getRole() != Role.TECHNICIAN || !inspection.getTechnician().getId().equals(actor.getId())) {
            throw new ForbiddenOperationException("Only the assigned technician can perform this action");
        }
    }

    public void requireReadAccess(Inspection inspection, User actor) {
        if (actor.getRole() == Role.ADMIN) return;
        if (actor.getRole() == Role.TECHNICIAN && inspection.getTechnician().getId().equals(actor.getId())) return;
        if (actor.getRole() == Role.SUPERVISOR && inspection.getSupervisor().getId().equals(actor.getId())) return;
        throw new ForbiddenOperationException("You do not have access to this inspection");
    }
}
