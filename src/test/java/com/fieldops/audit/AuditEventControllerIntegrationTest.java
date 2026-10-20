package com.fieldops.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fieldops.audit.domain.AuditEvent;
import com.fieldops.audit.repository.AuditEventRepository;
import com.fieldops.support.IntegrationTest;
import com.fieldops.support.TestUsers;
import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditEventControllerIntegrationTest extends IntegrationTest {

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private TestUsers testUsers;

    @Test
    void listsAuditEventsWhenOptionalTextFiltersAreOmitted() throws Exception {
        User admin = testUsers.create("audit-list-admin@fieldops.local", Role.ADMIN);
        AuditEvent event = new AuditEvent();
        event.setActorId(admin.getId());
        event.setAction("AUDIT_LIST_TEST");
        event.setEntityType("TestEntity");
        auditEventRepository.save(event);

        String token = login(admin.getEmail());
        String body = mockMvc.perform(get("/api/v1/audit-events?page=0&size=20&sort=occurredAt,desc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode response = objectMapper.readTree(body);
        assertTrue(response.get("totalElements").asInt() >= 1);
        JsonNode testEvent = null;
        for (JsonNode item : response.get("content")) {
            if ("AUDIT_LIST_TEST".equals(item.get("action").asText())) {
                testEvent = item;
                break;
            }
        }
        assertTrue(testEvent != null);
        assertEquals(admin.getName(), testEvent.get("actorName").asText());
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, TestUsers.RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
