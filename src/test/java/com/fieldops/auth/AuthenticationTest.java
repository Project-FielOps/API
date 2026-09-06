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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
                        .header("X-Request-ID", "qa-login-42")
                        .content("""
                                {"email":"auth-badpw@fieldops.local","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-ID", "qa-login-42"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.requestId").value("qa-login-42"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));
    }

    @Test
    void invalidRequestIdIsReplacedWithSafeCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/clients").header("X-Request-ID", "invalid id"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void healthEndpointIsPublicAndReturnsCorrelationId() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Request-ID", "qa-health-7"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "qa-health-7"))
                .andExpect(jsonPath("$.status").value("UP"));
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

    @Test
    void refreshRotatesSessionAndLogoutRevokesCurrentRefreshToken() throws Exception {
        User user = testUsers.create("auth-refresh@fieldops.local", Role.ADMIN);
        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(user.getEmail(), TestUsers.RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(loginBody).get("refreshToken").asText();

        String refreshBody = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String rotatedToken = objectMapper.readTree(refreshBody).get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"%s\"}".formatted(rotatedToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"%s\"}".formatted(rotatedToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changingPasswordInvalidatesAllRefreshSessions() throws Exception {
        User user = testUsers.create("auth-change-password@fieldops.local", Role.ADMIN);
        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(user.getEmail(), TestUsers.RAW_PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(loginBody).get("accessToken").asText();
        String refreshToken = objectMapper.readTree(loginBody).get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"NewPassword@123\"}"
                                .formatted(TestUsers.RAW_PASSWORD)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
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
