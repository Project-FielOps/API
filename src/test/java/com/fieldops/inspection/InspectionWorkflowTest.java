package com.fieldops.inspection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fieldops.support.IntegrationTest;
import com.fieldops.support.TestUsers;
import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end coverage of the core inspection lifecycle: template publish -> planning -> snapshot ->
 * execution -> submit validation -> review -> approval, exercising RN-024, RN-032..RN-044 and RN-079..RN-084.
 */
class InspectionWorkflowTest extends IntegrationTest {

    @Autowired
    private TestUsers testUsers;

    @Test
    void listsInspectionsWithoutOptionalFiltersOnPostgres() throws Exception {
        User admin = testUsers.create("inspection-list-admin@fieldops.local", Role.ADMIN);
        String adminToken = login(admin.getEmail());

        mockMvc.perform(get("/api/v1/inspections")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void fullLifecycleFromPlanningToApproval() throws Exception {
        User admin = testUsers.create("wf-admin@fieldops.local", Role.ADMIN);
        User supervisor = testUsers.create("wf-supervisor@fieldops.local", Role.SUPERVISOR);
        User otherSupervisor = testUsers.create("wf-supervisor2@fieldops.local", Role.SUPERVISOR);
        User technician = testUsers.create("wf-technician@fieldops.local", Role.TECHNICIAN);
        User otherTechnician = testUsers.create("wf-technician2@fieldops.local", Role.TECHNICIAN);

        String adminToken = login(admin.getEmail());
        String supervisorToken = login(supervisor.getEmail());
        String otherSupervisorToken = login(otherSupervisor.getEmail());
        String technicianToken = login(technician.getEmail());
        String otherTechnicianToken = login(otherTechnician.getEmail());

        // --- Cadastros (client -> site -> equipment) ---
        String clientId = apiPost("/api/v1/clients", adminToken,
                "{\"name\":\"Acme Ltda\",\"legalName\":null,\"document\":null,\"email\":null,\"phone\":null}", 201).get("id").asText();
        String siteId = apiPost("/api/v1/clients/%s/sites".formatted(clientId), adminToken,
                "{\"name\":\"Plant 1\",\"description\":null,\"addressLine\":null,\"city\":null,\"state\":null,\"postalCode\":null,\"latitude\":null,\"longitude\":null,\"contactName\":null,\"contactPhone\":null}", 201).get("id").asText();
        String equipmentId = apiPost("/api/v1/sites/%s/equipment".formatted(siteId), adminToken,
                "{\"name\":\"Generator GD-01\",\"assetNumber\":\"AN-1\",\"serialNumber\":\"SN-1\",\"manufacturer\":null,\"model\":null,\"description\":null,\"qrCode\":\"QR-WF-001\",\"installedAt\":null}", 201).get("id").asText();

        // --- Template: draft, section, two items (one required+critical), publish ---
        String templateId = apiPost("/api/v1/inspection-templates", supervisorToken,
                "{\"title\":\"Generator Checklist\",\"description\":null,\"category\":\"Electrical\"}", 201).get("id").asText();
        String sectionId = apiPost("/api/v1/inspection-templates/%s/sections".formatted(templateId), supervisorToken,
                "{\"title\":\"Condition\",\"description\":null,\"displayOrder\":1}", 201).get("id").asText();
        apiPost("/api/v1/inspection-templates/%s/sections/%s/items".formatted(templateId, sectionId), supervisorToken,
                "{\"title\":\"Oil level adequate?\",\"responseType\":\"CONFORMITY\",\"required\":true,\"observationRequiredOnFailure\":true,\"evidenceRequiredOnFailure\":true,\"displayOrder\":1}", 201);
        apiPost("/api/v1/inspection-templates/%s/sections/%s/items".formatted(templateId, sectionId), supervisorToken,
                "{\"title\":\"Notes\",\"responseType\":\"TEXT_SHORT\",\"required\":false,\"observationRequiredOnFailure\":false,\"evidenceRequiredOnFailure\":false,\"displayOrder\":2}", 201);
        apiPost("/api/v1/inspection-templates/%s/publish".formatted(templateId), supervisorToken, null, 200);

        // --- Planejamento: create inspection ---
        String scheduledFor = Instant.now().plus(1, ChronoUnit.DAYS).toString();
        JsonNode inspection = apiPost("/api/v1/inspections", supervisorToken, """
                {"templateId":"%s","clientId":"%s","siteId":"%s","equipmentId":"%s","technicianId":"%s","supervisorId":"%s",
                 "title":"Generator inspection","instructions":"Check carefully","priority":"HIGH","scheduledFor":"%s"}
                """.formatted(templateId, clientId, siteId, equipmentId, technician.getId(), supervisor.getId(), scheduledFor), 201);
        String inspectionId = inspection.get("id").asText();
        apiGet("/api/v1/inspections?status=ASSIGNED&priority=HIGH&technicianId=%s&supervisorId=%s&clientId=%s&siteId=%s&scheduledFrom=%s&scheduledTo=%s&overdue=false&awaitingReview=false&page=0&size=20&sort=scheduledFor,asc"
                .formatted(technician.getId(), supervisor.getId(), clientId, siteId, scheduledFor, scheduledFor),
                adminToken, 200);
        JsonNode supervisorInspections = apiGet("/api/v1/inspections", supervisorToken, 200);
        org.junit.jupiter.api.Assertions.assertEquals(1, supervisorInspections.get("totalElements").asInt());
        JsonNode otherSupervisorInspections = apiGet("/api/v1/inspections", otherSupervisorToken, 200);
        org.junit.jupiter.api.Assertions.assertEquals(0, otherSupervisorInspections.get("totalElements").asInt());
        String requiredItemId = null;
        for (JsonNode item : inspection.get("items")) {
            if (item.get("itemTitle").asText().equals("Oil level adequate?")) requiredItemId = item.get("id").asText();
        }
        org.junit.jupiter.api.Assertions.assertNotNull(requiredItemId);

        // RN-033: only the assigned technician may start the inspection.
        apiPost("/api/v1/inspections/%s/start".formatted(inspectionId), otherTechnicianToken, null, 403);
        apiPost("/api/v1/inspections/%s/start".formatted(inspectionId), adminToken, null, 403);

        apiPost("/api/v1/inspections/%s/start".formatted(inspectionId), technicianToken, null, 200);

        // RN-037: submit must fail while the required item is unanswered.
        apiPost("/api/v1/inspections/%s/submit".formatted(inspectionId), technicianToken, null, 422);

        // RN-038: a non-conforming answer without an observation must also block submission.
        JsonNode nonConformingAnswer = apiPut("/api/v1/inspections/%s/responses/%s".formatted(inspectionId, requiredItemId), technicianToken,
                "{\"inspectionItemId\":\"%s\",\"conformity\":\"NON_CONFORMING\"}".formatted(requiredItemId), 200);
        apiPost("/api/v1/inspections/%s/submit".formatted(inspectionId), technicianToken, null, 422);

        // Now answer with observation but still no evidence -> still blocked (RN-039, item configured evidence-required-on-failure).
        nonConformingAnswer = apiPut("/api/v1/inspections/%s/responses/%s".formatted(inspectionId, requiredItemId), technicianToken,
                "{\"inspectionItemId\":\"%s\",\"conformity\":\"NON_CONFORMING\",\"observation\":\"Oil is low\"}".formatted(requiredItemId), 200);
        apiPost("/api/v1/inspections/%s/submit".formatted(inspectionId), technicianToken, null, 422);

        MockMultipartFile evidence = new MockMultipartFile("file", "oil.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9});
        mockMvc.perform(multipart("/api/v1/inspections/{id}/evidence", inspectionId)
                        .file(evidence)
                        .param("responseId", nonConformingAnswer.get("id").asText())
                        .header("Authorization", "Bearer " + technicianToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("image/jpeg"));

        // Evidence metadata and content are readable by the assigned supervisor only among reviewers.
        JsonNode evidenceList = apiGetList("/api/v1/inspections/%s/evidence".formatted(inspectionId), supervisorToken);
        String evidenceId = evidenceList.get(0).get("id").asText();
        mockMvc.perform(get("/api/v1/inspections/{inspectionId}/evidence/{evidenceId}/content", inspectionId, evidenceId)
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9}));
        mockMvc.perform(get("/api/v1/inspections/{inspectionId}/evidence/{evidenceId}/content", inspectionId, evidenceId)
                        .header("Authorization", "Bearer " + otherSupervisorToken))
                .andExpect(status().isForbidden());

        // Switch the answer to CONFORMING so submit can proceed without needing an evidence upload in this test.
        apiPut("/api/v1/inspections/%s/responses/%s".formatted(inspectionId, requiredItemId), technicianToken,
                "{\"inspectionItemId\":\"%s\",\"conformity\":\"CONFORMING\"}".formatted(requiredItemId), 200);
        apiPost("/api/v1/inspections/%s/submit".formatted(inspectionId), technicianToken, null, 200);

        // RN-079: only the assigned supervisor may begin the review.
        apiPost("/api/v1/inspections/%s/begin-review".formatted(inspectionId), otherSupervisorToken, null, 403);
        apiPost("/api/v1/inspections/%s/begin-review".formatted(inspectionId), adminToken, null, 403);

        apiPost("/api/v1/inspections/%s/begin-review".formatted(inspectionId), supervisorToken, null, 200);

        // Review decisions are object-scoped and only valid once per current review state.
        apiPost("/api/v1/inspections/%s/approve".formatted(inspectionId), otherSupervisorToken, null, 403);
        apiPost("/api/v1/inspections/%s/approve".formatted(inspectionId), adminToken, null, 403);
        apiPost("/api/v1/inspections/%s/approve".formatted(inspectionId), technicianToken, null, 403);

        // RN-080: rejection requires a reason.
        mockMvc.perform(post("/api/v1/inspections/{id}/reject", inspectionId)
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());

        apiPost("/api/v1/inspections/%s/reject".formatted(inspectionId), supervisorToken,
                "{\"reason\":\"Photo missing for item 4\",\"comments\":null}", 200);

        JsonNode reviews = apiGetList("/api/v1/inspections/%s/reviews".formatted(inspectionId), supervisorToken);
        org.junit.jupiter.api.Assertions.assertEquals(1, reviews.size());
        org.junit.jupiter.api.Assertions.assertEquals("REJECTED", reviews.get(0).get("decision").asText());

        // Correction requires an explicit REJECTED -> IN_PROGRESS transition.
        apiPost("/api/v1/inspections/%s/submit".formatted(inspectionId), technicianToken, null, 422);
        apiPost("/api/v1/inspections/%s/reopen-correction".formatted(inspectionId), technicianToken, null, 200);
        // Technician corrects and resubmits; supervisor approves on the second cycle.
        apiPost("/api/v1/inspections/%s/submit".formatted(inspectionId), technicianToken, null, 200);
        apiPost("/api/v1/inspections/%s/begin-review".formatted(inspectionId), supervisorToken, null, 200);
        apiPost("/api/v1/inspections/%s/approve".formatted(inspectionId), supervisorToken, "{\"comments\":\"Looks good now\"}", 200);
        apiPost("/api/v1/inspections/%s/approve".formatted(inspectionId), supervisorToken, null, 409);
        apiPost("/api/v1/inspections/%s/reject".formatted(inspectionId), supervisorToken,
                "{\"reason\":\"Duplicate decision\"}", 409);

        JsonNode finalState = apiGet("/api/v1/inspections/%s".formatted(inspectionId), supervisorToken, 200);
        org.junit.jupiter.api.Assertions.assertEquals("APPROVED", finalState.get("status").asText());

        JsonNode allReviews = apiGetList("/api/v1/inspections/%s/reviews".formatted(inspectionId), supervisorToken);
        org.junit.jupiter.api.Assertions.assertEquals(2, allReviews.size());

        // RN-082: an approved inspection is locked from further edits.
        apiPut("/api/v1/inspections/%s/responses/%s".formatted(inspectionId, requiredItemId), technicianToken,
                "{\"inspectionItemId\":\"%s\",\"conformity\":\"CONFORMING\"}".formatted(requiredItemId), 422);

        // RN-086: every transition wrote an audit trail.
        JsonNode history = apiGetList("/api/v1/inspections/%s/history".formatted(inspectionId), supervisorToken);
        org.junit.jupiter.api.Assertions.assertTrue(history.size() >= 6);
        apiGet("/api/v1/inspections/%s/history".formatted(inspectionId), otherSupervisorToken, 403);
        apiGet("/api/v1/inspections/%s/history".formatted(inspectionId), technicianToken, 403);
    }

    @Test
    void rejectsUnsupportedInspectionFilterValuesInsteadOfIgnoringThem() throws Exception {
        User admin = testUsers.create("inspection-filter-contract-admin@fieldops.local", Role.ADMIN);
        String adminToken = login(admin.getEmail());

        mockMvc.perform(get("/api/v1/inspections")
                        .param("status", "NOT_A_STATUS")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/inspections")
                        .param("priority", "NOT_A_PRIORITY")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
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
        var req = post(url).header("Authorization", "Bearer " + token).contentType("application/json");
        if (body != null) req = req.content(body);
        else req = req.content("{}");
        String response = mockMvc.perform(req)
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return response.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response);
    }

    private JsonNode apiPut(String url, String token, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(put(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return response.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response);
    }

    private JsonNode apiGet(String url, String token, int expectedStatus) throws Exception {
        String response = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode apiGetList(String url, String token) throws Exception {
        return apiGet(url, token, 200);
    }
}
