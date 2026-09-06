package com.fieldops.auth;

import com.fieldops.support.IntegrationTest;
import com.fieldops.support.TestUsers;
import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class AuthenticationTest extends IntegrationTest {

    @Autowired
    private TestUsers testUsers;

    @Test
    void loginWithValidCredentialsReturnsTokens() throws Exception {
        testUsers.create("auth-ok@fieldops.local", Role.ADMIN);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"auth-ok@fieldops.local","password":"%s"}
                                """.formatted(TestUsers.RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void loginWithWrongPasswordReturns401WithStandardErrorBody() throws Exception {
        testUsers.create("auth-badpw@fieldops.local", Role.ADMIN);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"auth-badpw@fieldops.local","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));
    }

    @Test
    void inactiveUserCannotLogin() throws Exception {
        // RN-001: only active users may start a session.
        testUsers.createInactive("auth-inactive@fieldops.local", Role.TECHNICIAN);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"auth-inactive@fieldops.local","password":"%s"}
                                """.formatted(TestUsers.RAW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/clients"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meEndpointReturnsAuthenticatedUser() throws Exception {
        User user = testUsers.create("auth-me@fieldops.local", Role.SUPERVISOR);
        String token = login(user.getEmail());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.role").value("SUPERVISOR"));
    }

    @Test
    void technicianIsForbiddenFromUserManagement() throws Exception {
        // RN-003/RN-006: admin-only endpoints must reject other roles even when authenticated.
        User technician = testUsers.create("auth-tech@fieldops.local", Role.TECHNICIAN);
        String token = login(technician.getEmail());

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void supervisorCanListOnlyActiveTechniciansForAssignment() throws Exception {
        User supervisor = testUsers.create("auth-assign-supervisor@fieldops.local", Role.SUPERVISOR);
        testUsers.create("auth-assign-tech@fieldops.local", Role.TECHNICIAN);
        testUsers.createInactive("auth-assign-inactive-tech@fieldops.local", Role.TECHNICIAN);
        testUsers.create("auth-assign-other-supervisor@fieldops.local", Role.SUPERVISOR);
        String token = login(supervisor.getEmail());

        mockMvc.perform(get("/api/v1/users/assignable").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].email", hasItem("auth-assign-tech@fieldops.local")))
                .andExpect(jsonPath("$.content[*].email", not(hasItem("auth-assign-inactive-tech@fieldops.local"))))
                .andExpect(jsonPath("$.content[*].email", not(hasItem("auth-assign-other-supervisor@fieldops.local"))))
                .andExpect(jsonPath("$.content[*].role", everyItem(is("TECHNICIAN"))))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("ACTIVE"))));
    }

    @Test
    void technicianCannotListAssignableUsers() throws Exception {
        User technician = testUsers.create("auth-assign-forbidden@fieldops.local", Role.TECHNICIAN);
        String token = login(technician.getEmail());

        mockMvc.perform(get("/api/v1/users/assignable").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
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
