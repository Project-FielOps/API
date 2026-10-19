package com.fieldops.shared.seed;

import com.fieldops.client.domain.Client;
import com.fieldops.client.domain.ClientStatus;
import com.fieldops.client.repository.ClientRepository;
import com.fieldops.equipment.domain.Equipment;
import com.fieldops.equipment.domain.EquipmentStatus;
import com.fieldops.equipment.repository.EquipmentRepository;
import com.fieldops.site.domain.InspectionSite;
import com.fieldops.site.domain.SiteStatus;
import com.fieldops.site.repository.InspectionSiteRepository;
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

/**
 * Seeds a minimal but usable data set for local development, demos and grading -
 * one user per role plus a couple of clients/sites/equipment. Runs only under the
 * "dev" profile and only if fieldops.seed.enabled=true (see application-dev.yml),
 * and is idempotent (skips if any user already exists).
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
        if (userRepository.count() > 0) {
            log.info("Seed data skipped: users already exist");
            return;
        }

        // Todos os usuários de desenvolvimento compartilham a senha configurada:
        // são contas descartáveis de um banco local, não identidades reais.
        final String password = seedProperties.password();
        User admin = newUser("FieldOps Admin", "admin@fieldops.local", password, Role.ADMIN, "+55 11 90000-0001");
        User supervisor = newUser("Sara Supervisor", "supervisor@fieldops.local", password, Role.SUPERVISOR, "+55 11 90000-0002");
        User technician = newUser("Tom Technician", "technician@fieldops.local", password, Role.TECHNICIAN, "+55 11 90000-0003");
        newUser("Carla Viewer", "viewer@fieldops.local", password, Role.CLIENT_VIEWER, "+55 11 90000-0004");
        userRepository.saveAll(java.util.List.of(admin, supervisor, technician));

        Client acme = newClient("Acme Manufacturing", "Acme Industria e Comercio Ltda", "acme@example.com");
        Client globex = newClient("Globex Logistics", "Globex Transportes S.A.", "contact@globex.example.com");
        clientRepository.saveAll(java.util.List.of(acme, globex));

        InspectionSite acmePlant = newSite(acme, "Acme Plant 1", "Sao Paulo", "SP");
        InspectionSite globexHub = newSite(globex, "Globex Distribution Hub", "Campinas", "SP");
        siteRepository.saveAll(java.util.List.of(acmePlant, globexHub));

        Equipment compressor = newEquipment(acmePlant, "Air Compressor A1", "AC-001", "QR-AC-001");
        Equipment forklift = newEquipment(globexHub, "Forklift F3", "FL-003", "QR-FL-003");
        equipmentRepository.saveAll(java.util.List.of(compressor, forklift));

        log.info("Seed data created: 4 users, 2 clients, 2 sites, 2 equipment. "
                + "Log in as admin@fieldops.local with the configured seed password.");
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

    private Equipment newEquipment(InspectionSite site, String name, String assetNumber, String qrCode) {
        Equipment equipment = new Equipment();
        equipment.setSite(site);
        equipment.setName(name);
        equipment.setAssetNumber(assetNumber);
        equipment.setQrCode(qrCode);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        return equipment;
    }
}
