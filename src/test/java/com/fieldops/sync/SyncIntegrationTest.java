package com.fieldops.sync;

import com.fieldops.support.IntegrationTest;
import com.fieldops.support.TestUsers;
import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.User;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SyncIntegrationTest extends IntegrationTest {

    @Autowired
    private TestUsers testUsers;

    @Test
    void pushPersistsRejectedOperationAndReplayIsIdempotent() throws Exception {
        User technician = testUsers.create("sync-tech@fieldops.local", Role.TECHNICIAN);
        String token = login(technician.getEmail());
        String operationId = UUID.randomUUID().toString();
        String body = """
                {
                  "deviceId": "%s",
                  "operations": [{
                    "operationId": "%s",
                    "type": "UNKNOWN",
                    "entityId": "%s",
                    "payload": {}
                  }]
                }
                """.formatted(UUID.randomUUID(), operationId, UUID.randomUUID());

        mockMvc.perform(post("/api/v1/mobile/sync/push")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].operationId").value(operationId))
                .andExpect(jsonPath("$.results[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.results[0].code").value("UNSUPPORTED_OPERATION"));

        mockMvc.perform(post("/api/v1/mobile/sync/push")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("ALREADY_APPLIED"));
    }

    @Test
    void pullStartsAtZeroAndReturnsNextCursor() throws Exception {
        User technician = testUsers.create("sync-pull@fieldops.local", Role.TECHNICIAN);
        String token = login(technician.getEmail());

        mockMvc.perform(get("/api/v1/mobile/sync/pull")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes").isArray())
                .andExpect(jsonPath("$.nextCursor").value("0"));
    }

    @Test
    void nonTechnicianCannotUseSync() throws Exception {
        User supervisor = testUsers.create("sync-supervisor@fieldops.local", Role.SUPERVISOR);
        String token = login(supervisor.getEmail());

        mockMvc.perform(get("/api/v1/mobile/sync/pull").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidCursorIsRejected() throws Exception {
        User technician = testUsers.create("sync-cursor@fieldops.local", Role.TECHNICIAN);
        String token = login(technician.getEmail());

        mockMvc.perform(get("/api/v1/mobile/sync/pull?cursor=not-a-cursor")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_SYNC_CURSOR"));
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(email, TestUsers.RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("accessToken").asText();
    }
}
