package com.fieldops.shared.seed;

import com.fieldops.client.domain.Client;
import com.fieldops.client.domain.ClientStatus;
import com.fieldops.client.repository.ClientRepository;
import com.fieldops.equipment.domain.Equipment;
import com.fieldops.equipment.domain.EquipmentStatus;
import com.fieldops.equipment.repository.EquipmentRepository;
import com.fieldops.inspection.domain.Inspection;
import com.fieldops.inspection.domain.InspectionItemSnapshot;
import com.fieldops.inspection.domain.InspectionStatus;
import com.fieldops.inspection.domain.Priority;
import com.fieldops.inspection.repository.InspectionItemSnapshotRepository;
import com.fieldops.inspection.repository.InspectionRepository;
import com.fieldops.review.domain.NonConformity;
import com.fieldops.review.domain.NonConformitySeverity;
import com.fieldops.review.domain.NonConformityStatus;
import com.fieldops.review.repository.NonConformityRepository;
import com.fieldops.site.domain.InspectionSite;
import com.fieldops.site.domain.SiteStatus;
import com.fieldops.site.repository.InspectionSiteRepository;
import com.fieldops.template.domain.InspectionTemplate;
import com.fieldops.template.domain.InspectionTemplateVersion;
import com.fieldops.template.domain.ResponseType;
import com.fieldops.template.domain.TemplateSection;
import com.fieldops.template.domain.TemplateStatus;
import com.fieldops.template.repository.InspectionTemplateRepository;
import com.fieldops.template.repository.InspectionTemplateVersionRepository;
import com.fieldops.template.repository.TemplateSectionRepository;
import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.User;
import com.fieldops.user.domain.UserStatus;
import com.fieldops.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Seeds a minimal but usable data set for local development, demos and grading -
 * one user per role plus clients, sites, equipment, a published template, an
 * assigned inspection and an open non-conformity. Runs only under the "dev" or
 * "e2e" profile and only if fieldops.seed.enabled=true (see application-dev.yml).
 * It is idempotent and complements data that is already present in the database.
 */
@Component
@Profile({"dev", "e2e"})
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final InspectionSiteRepository siteRepository;
    private final EquipmentRepository equipmentRepository;
    private final InspectionTemplateRepository templateRepository;
    private final InspectionTemplateVersionRepository templateVersionRepository;
    private final TemplateSectionRepository templateSectionRepository;
    private final InspectionRepository inspectionRepository;
    private final InspectionItemSnapshotRepository snapshotRepository;
    private final NonConformityRepository nonConformityRepository;
    private final PasswordEncoder passwordEncoder;
    private final SeedProperties seedProperties;

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedProperties.enabled()) {
            return;
        }
        if (!seedProperties.hasPassword()) {
            log.warn("Seed data skipped: set FIELDOPS_SEED_PASSWORD to create the development users.");
            return;
        }
        // Todos os usuários de desenvolvimento compartilham a senha configurada:
        // são contas descartáveis de um banco local, não identidades reais.
        final String password = seedProperties.password();
        User admin = getOrCreateUser("FieldOps Admin", "admin@fieldops.local", password, Role.ADMIN, "+55 11 90000-0001");
        User supervisor = getOrCreateUser("Sara Supervisor", "supervisor@fieldops.local", password, Role.SUPERVISOR, "+55 11 90000-0002");
        User technician = getOrCreateUser("Tom Technician", "technician@fieldops.local", password, Role.TECHNICIAN, "+55 11 90000-0003");
        getOrCreateUser("Carla Viewer", "viewer@fieldops.local", password, Role.CLIENT_VIEWER, "+55 11 90000-0004");

        Client acme = findOrCreateClient("Acme Manufacturing", "Acme Industria e Comercio Ltda", "acme@example.com");
        findOrCreateClient("Globex Logistics", "Globex Transportes S.A.", "contact@globex.example.com");

        InspectionSite acmePlant = findOrCreateSite(acme, "Acme Plant 1", "Sao Paulo", "SP");
        InspectionSite globexHub = findOrCreateSite(
                clientRepository.findAll().stream()
                        .filter(client -> "Globex Logistics".equals(client.getName()))
                        .findFirst().orElseThrow(),
                "Globex Distribution Hub", "Campinas", "SP");

        Equipment compressor = findOrCreateEquipment(acmePlant, "Air Compressor A1", "AC-001", "QR-AC-001");
        findOrCreateEquipment(globexHub, "Forklift F3", "FL-003", "QR-FL-003");

        InspectionTemplateVersion templateVersion = findOrCreateTemplate(admin);
        Inspection inspection = inspectionRepository.findByTitle("Inspeção demonstrativa - Segurança operacional")
                .orElseGet(() -> createInspection(templateVersion, acme, acmePlant, compressor, technician, supervisor, admin));
        InspectionItemSnapshot snapshot = snapshotRepository.findByInspectionIdOrderBySectionOrderAscItemOrderAsc(inspection.getId())
                .stream().findFirst().orElseGet(() -> createSnapshot(inspection));

        boolean nonConformityExists = nonConformityRepository.findByInspectionId(inspection.getId()).stream()
                .anyMatch(item -> "Proteção do equipamento ausente".equals(item.getTitle()));
        if (!nonConformityExists) {
            NonConformity nonConformity = new NonConformity();
            nonConformity.setInspection(inspection);
            nonConformity.setInspectionItem(snapshot);
            nonConformity.setTitle("Proteção do equipamento ausente");
            nonConformity.setDescription("A proteção do compressor não estava instalada durante a inspeção.");
            nonConformity.setSeverity(NonConformitySeverity.HIGH);
            nonConformity.setStatus(NonConformityStatus.OPEN);
            nonConformity.setCreatedBy(technician);
            nonConformity.setCreatedAtDevice(Instant.now());
            nonConformity.setServerReceivedAt(Instant.now());
            nonConformityRepository.save(nonConformity);
        }

        log.info("Seed data ensured: development users, clients, sites, equipment, "
                + "1 published template, 1 inspection and 1 non-conformity. "
                + "Log in as admin@fieldops.local with the configured seed password.");
    }

    private User getOrCreateUser(String name, String email, String rawPassword, Role role, String phone) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(newUser(name, email, rawPassword, role, phone)));
    }

    private User newUser(String name, String email, String rawPassword, Role role, String phone) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPhone(phone);
        return user;
    }

    private Client findOrCreateClient(String name, String legalName, String email) {
        return clientRepository.findAll().stream()
                .filter(client -> name.equals(client.getName()))
                .findFirst()
                .orElseGet(() -> clientRepository.save(newClient(name, legalName, email)));
    }

    private Client newClient(String name, String legalName, String email) {
        Client client = new Client();
        client.setName(name);
        client.setLegalName(legalName);
        client.setEmail(email);
        client.setStatus(ClientStatus.ACTIVE);
        return client;
    }

    private InspectionSite newSite(Client client, String name, String city, String state) {
        InspectionSite site = new InspectionSite();
        site.setClient(client);
        site.setName(name);
        site.setCity(city);
        site.setState(state);
        site.setStatus(SiteStatus.ACTIVE);
        return site;
    }

    private InspectionSite findOrCreateSite(Client client, String name, String city, String state) {
        return siteRepository.findAll().stream()
                .filter(site -> name.equals(site.getName()) && site.getClient().getId().equals(client.getId()))
                .findFirst()
                .orElseGet(() -> siteRepository.save(newSite(client, name, city, state)));
    }

    private Equipment newEquipment(InspectionSite site, String name, String assetNumber, String qrCode) {
        Equipment equipment = new Equipment();
        equipment.setSite(site);
        equipment.setName(name);
        equipment.setAssetNumber(assetNumber);
        equipment.setQrCode(qrCode);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        return equipment;
    }

    private Equipment findOrCreateEquipment(InspectionSite site, String name, String assetNumber, String qrCode) {
        return equipmentRepository.findByQrCode(qrCode)
                .orElseGet(() -> equipmentRepository.save(newEquipment(site, name, assetNumber, qrCode)));
    }

    private InspectionTemplateVersion findOrCreateTemplate(User admin) {
        InspectionTemplate template = templateRepository.findAll().stream()
                .filter(item -> "Checklist demonstrativo - Segurança operacional".equals(item.getTitle()))
                .findFirst()
                .orElseGet(() -> {
                    InspectionTemplate created = new InspectionTemplate();
                    created.setTitle("Checklist demonstrativo - Segurança operacional");
                    created.setDescription("Checklist de demonstração para inspeções e não conformidades.");
                    created.setCategory("e2e");
                    created.setStatus(TemplateStatus.ACTIVE);
                    created.setCurrentVersion(1);
                    created.setCreatedBy(admin);
                    return templateRepository.save(created);
                });

        return templateVersionRepository.findTopByTemplateIdAndPublishedAtIsNotNullOrderByVersionNumberDesc(template.getId())
                .orElseGet(() -> {
                    InspectionTemplateVersion version = new InspectionTemplateVersion();
                    version.setTemplate(template);
                    version.setVersionNumber(1);
                    version.setTitleSnapshot(template.getTitle());
                    version.setDescriptionSnapshot(template.getDescription());
                    version.setPublishedBy(admin);
                    version.setPublishedAt(Instant.now());
                    version.setActiveForNewInspections(true);
                    InspectionTemplateVersion savedVersion = templateVersionRepository.save(version);

                    TemplateSection section = new TemplateSection();
                    section.setTemplateVersion(savedVersion);
                    section.setTitle("Segurança do equipamento");
                    section.setDescription("Verificações básicas de segurança operacional.");
                    section.setDisplayOrder(1);
                    templateSectionRepository.save(section);
                    return savedVersion;
                });
    }

    private Inspection createInspection(InspectionTemplateVersion templateVersion, Client client,
                                        InspectionSite site, Equipment equipment, User technician,
                                        User supervisor, User admin) {
        Inspection inspection = new Inspection();
        inspection.setTemplateVersion(templateVersion);
        inspection.setClient(client);
        inspection.setSite(site);
        inspection.setEquipment(equipment);
        inspection.setTechnician(technician);
        inspection.setSupervisor(supervisor);
        inspection.setCreatedBy(admin);
        inspection.setTitle("Inspeção demonstrativa - Segurança operacional");
        inspection.setInstructions("Inspeção criada automaticamente para demonstração do fluxo.");
        inspection.setPriority(Priority.HIGH);
        inspection.setStatus(InspectionStatus.SUBMITTED);
        inspection.setScheduledFor(Instant.now().minusSeconds(3600));
        inspection.setSubmittedAtServer(Instant.now());
        return inspectionRepository.save(inspection);
    }

    private InspectionItemSnapshot createSnapshot(Inspection inspection) {
        InspectionItemSnapshot snapshot = new InspectionItemSnapshot();
        snapshot.setInspection(inspection);
        snapshot.setSectionTitle("Segurança do equipamento");
        snapshot.setSectionDescription("Verificações básicas de segurança operacional.");
        snapshot.setSectionOrder(1);
        snapshot.setItemCode("SEG-001");
        snapshot.setItemTitle("A proteção do equipamento está instalada?");
        snapshot.setItemDescription("Verifique se as partes móveis estão protegidas.");
        snapshot.setResponseType(ResponseType.CONFORMITY);
        snapshot.setRequired(true);
        snapshot.setItemOrder(1);
        return snapshotRepository.save(snapshot);
    }
}
