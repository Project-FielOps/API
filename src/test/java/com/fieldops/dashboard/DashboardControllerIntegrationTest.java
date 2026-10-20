package com.fieldops.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fieldops.support.IntegrationTest;
import com.fieldops.support.TestUsers;
import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the public dashboard endpoint against the real PostgreSQL/Testcontainers database.
 * The assertions use deltas for global counters because the integration suite shares its test
 * application context, while supervisor counters use isolated users created by this test.
 */
class DashboardControllerIntegrationTest extends IntegrationTest {

    @Autowired
    private TestUsers testUsers;

    @Test
    void summaryUsesGlobalAdminCountsAndOnlyAssignedSupervisorData() throws Exception {
        User admin = testUsers.create("dashboard-admin-integration@fieldops.local", Role.ADMIN);
        User assignedSupervisor = testUsers.create("dashboard-assigned-supervisor@fieldops.local", Role.SUPERVISOR);
        User otherSupervisor = testUsers.create("dashboard-other-supervisor@fieldops.local", Role.SUPERVISOR);
        User emptySupervisor = testUsers.create("dashboard-empty-supervisor@fieldops.local", Role.SUPERVISOR);
        User assignedTechnician = testUsers.create("dashboard-assigned-technician@fieldops.local", Role.TECHNICIAN);
        User otherTechnician = testUsers.create("dashboard-other-technician@fieldops.local", Role.TECHNICIAN);

        String adminToken = login(admin.getEmail());
        String assignedSupervisorToken = login(assignedSupervisor.getEmail());
        String otherSupervisorToken = login(otherSupervisor.getEmail());
        String emptySupervisorToken = login(emptySupervisor.getEmail());
        String assignedTechnicianToken = login(assignedTechnician.getEmail());

        JsonNode adminBefore = dashboard(adminToken);
        long assignedBefore = adminBefore.at("/inspectionsByStatus/ASSIGNED").asLong();
        long criticalNcBefore = adminBefore.at("/nonConformitiesBySeverity/CRITICAL").asLong();

        String clientId = apiPost("/api/v1/clients", adminToken,
                "{\"name\":\"Dashboard Client\",\"legalName\":null,\"document\":null,\"email\":null,\"phone\":null}", 201)
                .get("id").asText();
        String siteId = apiPost("/api/v1/clients/%s/sites".formatted(clientId), adminToken,
                "{\"name\":\"Dashboard Site\",\"description\":null,\"addressLine\":null,\"city\":null,\"state\":null,\"postalCode\":null,\"latitude\":null,\"longitude\":null,\"contactName\":null,\"contactPhone\":null}", 201)
                .get("id").asText();
        String equipmentId = apiPost("/api/v1/sites/%s/equipment".formatted(siteId), adminToken,
                "{\"name\":\"Dashboard Equipment\",\"assetNumber\":\"DASH-01\",\"serialNumber\":\"DASH-SN-01\",\"manufacturer\":null,\"model\":null,\"description\":null,\"qrCode\":\"DASH-QR-01\",\"installedAt\":null}", 201)
                .get("id").asText();

        String templateId = apiPost("/api/v1/inspection-templates", assignedSupervisorToken,
                "{\"title\":\"Dashboard Template\",\"description\":null,\"category\":\"Dashboard\"}", 201)
                .get("id").asText();
        String sectionId = apiPost("/api/v1/inspection-templates/%s/sections".formatted(templateId), assignedSupervisorToken,
                "{\"title\":\"Dashboard Section\",\"description\":null,\"displayOrder\":1}", 201)
                .get("id").asText();
        apiPost("/api/v1/inspection-templates/%s/sections/%s/items".formatted(templateId, sectionId), assignedSupervisorToken,
                "{\"title\":\"Dashboard Check\",\"responseType\":\"CONFORMITY\",\"required\":false,\"observationRequiredOnFailure\":false,\"evidenceRequiredOnFailure\":false,\"displayOrder\":1}", 201);
        apiPost("/api/v1/inspection-templates/%s/publish".formatted(templateId), assignedSupervisorToken, null, 200);

        JsonNode overdueInspection = createInspection(templateId, clientId, siteId, equipmentId,
                assignedTechnician.getId().toString(), assignedSupervisor.getId().toString(),
                "Dashboard overdue assigned", Instant.now().minus(2, ChronoUnit.DAYS), assignedSupervisorToken);
        createInspection(templateId, clientId, siteId, equipmentId,
                otherTechnician.getId().toString(), otherSupervisor.getId().toString(),
                "Dashboard future other supervisor", Instant.now().plus(2, ChronoUnit.DAYS), assignedSupervisorToken);

        String overdueInspectionId = overdueInspection.get("id").asText();
        String snapshotItemId = overdueInspection.get("items").get(0).get("id").asText();
        apiPost("/api/v1/inspections/%s/non-conformities".formatted(overdueInspectionId), assignedTechnicianToken,
                "{\"inspectionItemId\":\"%s\",\"responseId\":null,\"title\":\"Dashboard critical finding\",\"description\":\"Critical finding created for dashboard integration coverage\",\"severity\":\"CRITICAL\"}".formatted(snapshotItemId), 201);

        JsonNode adminNonConformities = apiGet("/api/v1/non-conformities?inspectionId=%s&severity=CRITICAL&page=0&size=20".formatted(overdueInspectionId), adminToken, 200);
        assertEquals(1, adminNonConformities.get("content").size(),
                "ADMIN must see non-conformities across inspections");
        assertEquals("Dashboard critical finding", adminNonConformities.get("content").get(0).get("title").asText());

        JsonNode assignedNonConformities = apiGet("/api/v1/non-conformities", assignedSupervisorToken, 200);
        assertEquals(1, assignedNonConformities.get("content").size(),
                "Supervisor must see non-conformities from assigned inspections");
        JsonNode otherNonConformities = apiGet("/api/v1/non-conformities", otherSupervisorToken, 200);
        assertEquals(0, otherNonConformities.get("content").size(),
                "Supervisor must not see another supervisor's non-conformities");

        JsonNode adminAfter = dashboard(adminToken);
        assertEquals(assignedBefore + 2, adminAfter.at("/inspectionsByStatus/ASSIGNED").asLong(),
                "ADMIN must see both inspections in the global count");
        assertEquals(criticalNcBefore + 1, adminAfter.at("/nonConformitiesBySeverity/CRITICAL").asLong(),
                "ADMIN must see the critical NC globally");
        assertEquals(1, adminAfter.get("overdueCount").asLong(),
                "ADMIN must see the one overdue open inspection created by this test");

        JsonNode assignedSummary = dashboard(assignedSupervisorToken);
        assertEquals(1, assignedSummary.at("/inspectionsByStatus/ASSIGNED").asLong(),
                "Assigned supervisor must see only its inspection");
        assertEquals(1, assignedSummary.at("/nonConformitiesBySeverity/CRITICAL").asLong(),
                "Assigned supervisor must see the NC linked to its inspection");
        assertEquals(1, assignedSummary.get("overdueCount").asLong());

        JsonNode otherSummary = dashboard(otherSupervisorToken);
        assertEquals(1, otherSummary.at("/inspectionsByStatus/ASSIGNED").asLong(),
                "The second supervisor must see its own inspection");
        assertEquals(0, otherSummary.at("/nonConformitiesBySeverity/CRITICAL").asLong(),
                "The second supervisor must not see another supervisor's NC");
        assertEquals(0, otherSummary.get("overdueCount").asLong(),
                "The second supervisor has no overdue inspection");

        JsonNode emptySummary = dashboard(emptySupervisorToken);
        assertEquals(0, emptySummary.at("/inspectionsByStatus/ASSIGNED").asLong(),
                "A supervisor without assigned inspections must receive zero inspection count");
        assertEquals(0, emptySummary.at("/nonConformitiesBySeverity/CRITICAL").asLong(),
                "A supervisor without assigned inspections must receive zero NC count");
        assertEquals(0, emptySummary.get("overdueCount").asLong(),
                "A supervisor without assigned inspections must receive zero overdue count");
    }

    @Test
    void technicianCannotAccessDashboardSummary() throws Exception {
        User technician = testUsers.create("dashboard-technician-forbidden@fieldops.local", Role.TECHNICIAN);
        String technicianToken = login(technician.getEmail());

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + technicianToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUserCannotAccessDashboardSummary() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode createInspection(String templateId, String clientId, String siteId, String equipmentId,
                                    String technicianId, String supervisorId, String title, Instant scheduledFor,
                                    String creatorToken) throws Exception {
        return apiPost("/api/v1/inspections", creatorToken, """
                {"templateId":"%s","clientId":"%s","siteId":"%s","equipmentId":"%s",
                 "technicianId":"%s","supervisorId":"%s","title":"%s","instructions":null,
                 "priority":"MEDIUM","scheduledFor":"%s"}
                """.formatted(templateId, clientId, siteId, equipmentId, technicianId, supervisorId, title, scheduledFor), 201);
    }

    private JsonNode dashboard(String token) throws Exception {
        String response = mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode apiGet(String url, String token, int expectedStatus) throws Exception {
        String response = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, TestUsers.RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private JsonNode apiPost(String url, String token, String body, int expectedStatus) throws Exception {
        var request = post(url).header("Authorization", "Bearer " + token).contentType("application/json");
        if (body != null) {
            request = request.content(body);
        } else {
            request = request.content("{}");
        }
        String response = mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return response.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response);
    }
}
