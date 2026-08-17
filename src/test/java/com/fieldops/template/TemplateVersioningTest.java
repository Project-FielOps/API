package com.fieldops.template;

import com.fieldops.support.IntegrationTest;
import com.fieldops.support.TestUsers;
import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.User;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Covers RN-015..RN-023: draft editing, publish immutability, and new-version-on-change. */
class TemplateVersioningTest extends IntegrationTest {

    @Autowired
    private TestUsers testUsers;

    private String supervisorToken;

    @Test
    void draftCanBeBuiltAndPublishedThenBecomesImmutable() throws Exception {
        User supervisor = testUsers.create("tpl-supervisor@fieldops.local", Role.SUPERVISOR);
        supervisorToken = login(supervisor.getEmail());

        String templateId = createTemplate("Fire Extinguisher Checklist", "Safety");

        // A brand-new template cannot be published: it has no sections/items yet (RN-015).
        mockMvc.perform(post("/api/v1/inspection-templates/{id}/publish", templateId)
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMPTY_TEMPLATE"));

        String sectionId = addSection(templateId, "Physical condition", 1);
        String itemId = addItem(templateId, sectionId, "Seal is intact?", "CONFORMITY", true, true, true, 1);

        // Publishing now succeeds and creates version 1.
        mockMvc.perform(post("/api/v1/inspection-templates/{id}/publish", templateId)
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        // The published section is now immutable: editing it directly must fail (RN-019).
        mockMvc.perform(put("/api/v1/inspection-templates/{id}/sections/{sectionId}", templateId, sectionId)
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType("application/json")
                        .content("""
                                {"title":"Renamed","description":null,"displayOrder":1}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_VERSION_IMMUTABLE"));

        // Adding a section under the template now opens (or reuses) a new draft, leaving v1 untouched (RN-020).
        String secondSectionId = addSection(templateId, "Extra section", 2);
        assertThat(secondSectionId).isNotEqualTo(sectionId);

        mockMvc.perform(get("/api/v1/inspection-templates/{id}/versions", templateId)
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionNumber").value(1))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void itemRequiresResponseTypeAndTitle() throws Exception {
        User supervisor = testUsers.create("tpl-supervisor2@fieldops.local", Role.SUPERVISOR);
        String token = login(supervisor.getEmail());
        String templateId = createTemplate("Generator Checklist", "Electrical");
        String sectionId = addSection(templateId, "Identification", 1);

        mockMvc.perform(post("/api/v1/inspection-templates/{id}/sections/{sectionId}/items", templateId, sectionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"title":"","responseType":null,"required":true,"observationRequiredOnFailure":false,"evidenceRequiredOnFailure":false,"displayOrder":1}
                                """))
                .andExpect(status().isBadRequest());
    }

    private String createTemplate(String title, String category) throws Exception {
        User supervisor = testUsers.create(title.toLowerCase().replace(" ", "-") + "-owner@fieldops.local", Role.SUPERVISOR);
        String token = login(supervisor.getEmail());
        String body = mockMvc.perform(post("/api/v1/inspection-templates")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"title":"%s","description":"desc","category":"%s"}
                                """.formatted(title, category)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        this.supervisorToken = token;
        return objectMapper.readTree(body).get("id").asText();
    }

    private String addSection(String templateId, String title, int order) throws Exception {
        String body = mockMvc.perform(post("/api/v1/inspection-templates/{id}/sections", templateId)
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType("application/json")
                        .content("""
                                {"title":"%s","description":null,"displayOrder":%d}
                                """.formatted(title, order)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String addItem(String templateId, String sectionId, String title, String responseType,
                            boolean required, boolean obsOnFailure, boolean evidenceOnFailure, int order) throws Exception {
        String body = mockMvc.perform(post("/api/v1/inspection-templates/{id}/sections/{sectionId}/items", templateId, sectionId)
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType("application/json")
                        .content("""
                                {"title":"%s","responseType":"%s","required":%s,"observationRequiredOnFailure":%s,"evidenceRequiredOnFailure":%s,"displayOrder":%d}
                                """.formatted(title, responseType, required, obsOnFailure, evidenceOnFailure, order)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("id").asText();
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, TestUsers.RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
