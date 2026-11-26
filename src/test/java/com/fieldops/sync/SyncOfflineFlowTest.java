package com.fieldops.sync;

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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Jornada offline do técnico: tudo o que foi feito sem rede chega ao banco principal pelo
 * /mobile/sync (push idempotente) e pelo upload idempotente de evidências, e o que muda no
 * servidor desce para o aparelho pelo pull.
 */
class SyncOfflineFlowTest extends IntegrationTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};

    @Autowired
    private TestUsers testUsers;

    private record Scenario(String inspectionId, String requiredItemId, String notesItemId,
                            String technicianToken, String supervisorToken, String otherTechnicianToken,
                            User technician) {
    }

    private Scenario scenario(String prefix) throws Exception {
        User admin = testUsers.create(prefix + "-admin@fieldops.local", Role.ADMIN);
        User supervisor = testUsers.create(prefix + "-supervisor@fieldops.local", Role.SUPERVISOR);
        User technician = testUsers.create(prefix + "-tech@fieldops.local", Role.TECHNICIAN);
        User otherTechnician = testUsers.create(prefix + "-tech2@fieldops.local", Role.TECHNICIAN);
        String adminToken = login(admin.getEmail());
        String supervisorToken = login(supervisor.getEmail());

        String clientId = apiPost("/api/v1/clients", adminToken,
                "{\"name\":\"Cliente " + prefix + "\"}", 201).get("id").asText();
        String siteId = apiPost("/api/v1/clients/%s/sites".formatted(clientId), adminToken,
                "{\"name\":\"Planta " + prefix + "\"}", 201).get("id").asText();
        String templateId = apiPost("/api/v1/inspection-templates", supervisorToken,
                "{\"title\":\"Checklist " + prefix + "\",\"category\":\"Elétrica\"}", 201).get("id").asText();
        String sectionId = apiPost("/api/v1/inspection-templates/%s/sections".formatted(templateId), supervisorToken,
                "{\"title\":\"Condição\",\"displayOrder\":1}", 201).get("id").asText();
        apiPost("/api/v1/inspection-templates/%s/sections/%s/items".formatted(templateId, sectionId), supervisorToken,
                "{\"title\":\"Oil level\",\"responseType\":\"CONFORMITY\",\"required\":true,\"observationRequiredOnFailure\":true,\"evidenceRequiredOnFailure\":true,\"displayOrder\":1}", 201);
        apiPost("/api/v1/inspection-templates/%s/sections/%s/items".formatted(templateId, sectionId), supervisorToken,
                "{\"title\":\"Notes\",\"responseType\":\"TEXT_SHORT\",\"required\":false,\"displayOrder\":2}", 201);
        apiPost("/api/v1/inspection-templates/%s/publish".formatted(templateId), supervisorToken, null, 200);

        JsonNode inspection = apiPost("/api/v1/inspections", supervisorToken, """
                {"templateId":"%s","clientId":"%s","siteId":"%s","technicianId":"%s","supervisorId":"%s",
                 "title":"Inspeção %s","priority":"HIGH","scheduledFor":"%s"}
                """.formatted(templateId, clientId, siteId, technician.getId(), supervisor.getId(), prefix,
                Instant.now().plus(1, ChronoUnit.DAYS)), 201);
        String requiredItemId = null;
        String notesItemId = null;
        for (JsonNode item : inspection.get("items")) {
            if (item.get("itemTitle").asText().equals("Oil level")) requiredItemId = item.get("id").asText();
            if (item.get("itemTitle").asText().equals("Notes")) notesItemId = item.get("id").asText();
        }
        return new Scenario(inspection.get("id").asText(), requiredItemId, notesItemId,
                login(technician.getEmail()), supervisorToken, login(otherTechnician.getEmail()), technician);
    }

    @Test
    void rejectedOperationDoesNotAbortTheRestOfTheBatch() throws Exception {
        Scenario s = scenario("sync-batch");
        String deviceId = UUID.randomUUID().toString();
        String start = UUID.randomUUID().toString();
        String badAnswer = UUID.randomUUID().toString();
        String goodAnswer = UUID.randomUUID().toString();

        JsonNode results = push(s.technicianToken(), deviceId, """
                [{"operationId":"%s","type":"INSPECTION_START","entityId":"%s","payload":{}},
                 {"operationId":"%s","type":"ANSWER_UPSERT","entityId":"%s","payload":{"inspectionItemId":"%s"}},
                 {"operationId":"%s","type":"ANSWER_UPSERT","entityId":"%s","payload":{"inspectionItemId":"%s","valueText":"ok"}}]
                """.formatted(start, s.inspectionId(),
                badAnswer, s.inspectionId(), UUID.randomUUID(),
                goodAnswer, s.inspectionId(), s.notesItemId()));

        assertEquals("APPLIED", results.get(0).get("status").asText());
        assertEquals("REJECTED", results.get(1).get("status").asText());
        assertEquals("OBJECT_NOT_FOUND", results.get(1).get("code").asText());
        assertEquals("APPLIED", results.get(2).get("status").asText());

        JsonNode detail = apiGet("/api/v1/inspections/" + s.inspectionId(), s.technicianToken());
        assertEquals("IN_PROGRESS", detail.get("status").asText());
        JsonNode answers = apiGet("/api/v1/inspections/%s/responses".formatted(s.inspectionId()), s.technicianToken());
        assertEquals(1, answers.size());

        // O resultado de uma transição não pode vazar a entidade JPA (e o hash de senha do técnico).
        assertFalse(results.toString().contains("passwordHash"));
    }

    @Test
    void forbiddenOperationIsReportedPerOperationInsteadOfFailingTheRequest() throws Exception {
        Scenario s = scenario("sync-forbidden");
        JsonNode results = push(s.otherTechnicianToken(), UUID.randomUUID().toString(), """
                [{"operationId":"%s","type":"INSPECTION_START","entityId":"%s","payload":{}}]
                """.formatted(UUID.randomUUID(), s.inspectionId()));
        assertEquals("REJECTED", results.get(0).get("status").asText());
        assertEquals("FORBIDDEN_SCOPE", results.get(0).get("code").asText());
    }

    @Test
    void fullOfflineJourneyReachesTheMainDatabaseExactlyOnce() throws Exception {
        Scenario s = scenario("sync-journey");
        String deviceId = UUID.randomUUID().toString();
        String start = UUID.randomUUID().toString();
        String answer = UUID.randomUUID().toString();
        String nc = UUID.randomUUID().toString();
        String answerPhoto = UUID.randomUUID().toString();
        String ncPhoto = UUID.randomUUID().toString();
        String submit = UUID.randomUUID().toString();

        String structured = """
                [{"operationId":"%s","type":"INSPECTION_START","entityId":"%s","payload":{}},
                 {"operationId":"%s","type":"ANSWER_UPSERT","entityId":"%s","dependsOn":["%s"],
                  "payload":{"inspectionItemId":"%s","conformity":"NON_CONFORMING","observation":"Óleo baixo"}},
                 {"operationId":"%s","type":"NON_CONFORMITY_CREATE","entityId":"%s","dependsOn":["%s"],
                  "payload":{"inspectionItemId":"%s","title":"Vazamento","description":"Vazamento no cárter","severity":"CRITICAL"}}]
                """.formatted(start, s.inspectionId(),
                answer, s.inspectionId(), start, s.requiredItemId(),
                nc, s.inspectionId(), start, s.requiredItemId());

        JsonNode first = push(s.technicianToken(), deviceId, structured);
        for (JsonNode result : first) assertEquals("APPLIED", result.get("status").asText(), result.toString());

        // Reenvio do mesmo lote (resposta perdida na rede): nada é aplicado de novo.
        JsonNode replay = push(s.technicianToken(), deviceId, structured);
        for (JsonNode result : replay) assertEquals("ALREADY_APPLIED", result.get("status").asText());

        // Evidências vinculadas pelo item e pela NC criada offline, enviadas duas vezes cada.
        String firstEvidenceId = upload(s, answerPhoto, "inspectionItemId", s.requiredItemId(), 201);
        assertEquals(firstEvidenceId, upload(s, answerPhoto, "inspectionItemId", s.requiredItemId(), 200));
        upload(s, ncPhoto, "nonConformityOperationId", nc, 201);
        upload(s, ncPhoto, "nonConformityOperationId", nc, 200);

        JsonNode evidence = apiGet("/api/v1/inspections/%s/evidence".formatted(s.inspectionId()), s.supervisorToken());
        assertEquals(2, evidence.size());
        JsonNode ncs = apiGet("/api/v1/inspections/%s/non-conformities".formatted(s.inspectionId()), s.supervisorToken());
        assertEquals(1, ncs.size());
        assertNotNull(ncs.get(0).get("responseId").textValue(), "NC criada offline deve apontar para a resposta do item");

        JsonNode submitted = push(s.technicianToken(), deviceId, """
                [{"operationId":"%s","type":"INSPECTION_SUBMIT","entityId":"%s","payload":{}}]
                """.formatted(submit, s.inspectionId()));
        assertEquals("APPLIED", submitted.get(0).get("status").asText(), submitted.toString());
        assertEquals("SUBMITTED", submitted.get(0).get("payload").get("status").asText());

        // Replay da evidência depois do envio continua idempotente, sem esbarrar no bloqueio da inspeção.
        assertEquals(firstEvidenceId, upload(s, answerPhoto, "inspectionItemId", s.requiredItemId(), 200));

        JsonNode detail = apiGet("/api/v1/inspections/" + s.inspectionId(), s.supervisorToken());
        assertEquals("SUBMITTED", detail.get("status").asText());
    }

    @Test
    void pullDeliversServerSideChangesToTheAssignedTechnician() throws Exception {
        Scenario s = scenario("sync-pull");
        JsonNode initial = pull(s.technicianToken(), "0");
        assertTrue(containsEvent(initial, s.inspectionId(), "ASSIGNED"), initial.toString());

        String deviceId = UUID.randomUUID().toString();
        push(s.technicianToken(), deviceId, """
                [{"operationId":"%s","type":"INSPECTION_START","entityId":"%s","payload":{}},
                 {"operationId":"%s","type":"ANSWER_UPSERT","entityId":"%s","payload":{"inspectionItemId":"%s","conformity":"CONFORMING"}},
                 {"operationId":"%s","type":"INSPECTION_SUBMIT","entityId":"%s","payload":{}}]
                """.formatted(UUID.randomUUID(), s.inspectionId(),
                UUID.randomUUID(), s.inspectionId(), s.requiredItemId(),
                UUID.randomUUID(), s.inspectionId()));

        String cursor = initial.get("nextCursor").asText();
        apiPost("/api/v1/inspections/%s/begin-review".formatted(s.inspectionId()), s.supervisorToken(), null, 200);
        apiPost("/api/v1/inspections/%s/reject".formatted(s.inspectionId()), s.supervisorToken(),
                "{\"reason\":\"Foto ilegível\"}", 200);

        JsonNode afterReview = pull(s.technicianToken(), cursor);
        assertTrue(containsEvent(afterReview, s.inspectionId(), "REJECTED"), afterReview.toString());
        assertTrue(Long.parseLong(afterReview.get("nextCursor").asText()) > Long.parseLong(cursor));

        // Outro técnico não enxerga o feed de quem não é ele.
        JsonNode other = pull(s.otherTechnicianToken(), "0");
        assertFalse(other.toString().contains(s.inspectionId()));
    }

    private boolean containsEvent(JsonNode pull, String inspectionId, String event) {
        for (JsonNode change : pull.get("changes")) {
            if (change.get("entityId").asText().equals(inspectionId)
                    && event.equals(change.get("payload").path("event").asText())) {
                return true;
            }
        }
        return false;
    }

    private String upload(Scenario s, String operationId, String linkParam, String linkValue, int expected) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/inspections/{id}/evidence", s.inspectionId())
                        .file(new MockMultipartFile("file", "foto.jpg", "image/jpeg", JPEG))
                        .param("operationId", operationId)
                        .param(linkParam, linkValue)
                        .header("Authorization", "Bearer " + s.technicianToken()))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private JsonNode push(String token, String deviceId, String operations) throws Exception {
        String body = mockMvc.perform(post("/api/v1/mobile/sync/push")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"deviceId\":\"%s\",\"operations\":%s}".formatted(deviceId, operations)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("results");
    }

    private JsonNode pull(String token, String cursor) throws Exception {
        String body = mockMvc.perform(get("/api/v1/mobile/sync/pull").param("cursor", cursor)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
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
        String response = mockMvc.perform(post(url).header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(body == null ? "{}" : body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return response.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response);
    }

    private JsonNode apiGet(String url, String token) throws Exception {
        String response = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }
}
