package com.fieldops.auth;

import com.fieldops.support.IntegrationTest;
import com.fieldops.support.TestUsers;
import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.User;
import com.fieldops.user.repository.UserRepository;
import com.fieldops.auth.security.FieldOpsUserDetails;
import com.fieldops.auth.security.JwtService;
import com.fieldops.auth.application.EmailService;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

class AuthenticationTest extends IntegrationTest {

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private EmailService emailService;

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
    void validAccessTokenForDeletedUserReturns401InsteadOf500() throws Exception {
        User deletedUser = new User();
        deletedUser.setId(UUID.randomUUID());
        deletedUser.setEmail("deleted@fieldops.local");
        deletedUser.setRole(Role.ADMIN);
        deletedUser.setStatus(com.fieldops.user.domain.UserStatus.ACTIVE);

        String token = jwtService.generateAccessToken(new FieldOpsUserDetails(deletedUser));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void validRefreshTokenForDeletedUserReturnsInvalidCredentials() throws Exception {
        User deletedUser = new User();
        deletedUser.setId(UUID.randomUUID());
        deletedUser.setEmail("deleted-refresh@fieldops.local");
        deletedUser.setRole(Role.ADMIN);
        deletedUser.setStatus(com.fieldops.user.domain.UserStatus.ACTIVE);

        String token = jwtService.generateRefreshToken(new FieldOpsUserDetails(deletedUser));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"%s\"}".formatted(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
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
    void supervisorCanListOnlyActiveAssignableUsersByRoleAndStatus() throws Exception {
        User supervisor = testUsers.create("auth-assign-supervisor@fieldops.local", Role.SUPERVISOR);
        testUsers.create("auth-assign-tech@fieldops.local", Role.TECHNICIAN);
        testUsers.createInactive("auth-assign-inactive-tech@fieldops.local", Role.TECHNICIAN);
        testUsers.create("auth-assign-active-supervisor@fieldops.local", Role.SUPERVISOR);
        testUsers.createInactive("auth-assign-inactive-supervisor@fieldops.local", Role.SUPERVISOR);
        testUsers.create("auth-assign-admin@fieldops.local", Role.ADMIN);
        String token = login(supervisor.getEmail());

        mockMvc.perform(get("/api/v1/users/assignable").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].email", hasItem("auth-assign-tech@fieldops.local")))
                .andExpect(jsonPath("$.content[*].email", not(hasItem("auth-assign-inactive-tech@fieldops.local"))))
                .andExpect(jsonPath("$.content[*].email", not(hasItem("auth-assign-admin@fieldops.local"))))
                .andExpect(jsonPath("$.content[*].role", everyItem(is("TECHNICIAN"))))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("ACTIVE"))));

        mockMvc.perform(get("/api/v1/users/assignable")
                        .param("role", "SUPERVISOR")
                        .param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].email", hasItem("auth-assign-active-supervisor@fieldops.local")))
                .andExpect(jsonPath("$.content[*].email", not(hasItem("auth-assign-inactive-supervisor@fieldops.local"))))
                .andExpect(jsonPath("$.content[*].role", everyItem(is("SUPERVISOR"))))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("ACTIVE"))));

        mockMvc.perform(get("/api/v1/users/assignable")
                        .param("role", "SUPERVISOR")
                        .param("status", "INACTIVE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));

        mockMvc.perform(get("/api/v1/users/assignable")
                        .param("role", "ADMIN")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assignableUsersArePaginatedWithoutCrossRoleOrInactiveLeakage() throws Exception {
        User admin = testUsers.create("auth-assign-pagination-admin@fieldops.local", Role.ADMIN);
        for (int i = 0; i < 5; i++) {
            testUsers.create("auth-assign-pagination-tech-%d@fieldops.local".formatted(i), Role.TECHNICIAN);
        }
        testUsers.createInactive("auth-assign-pagination-inactive@fieldops.local", Role.TECHNICIAN);
        String token = login(admin.getEmail());

        mockMvc.perform(get("/api/v1/users/assignable")
                        .param("role", "TECHNICIAN")
                        .param("status", "ACTIVE")
                        .param("page", "1")
                        .param("size", "2")
                        .param("sort", "email,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(1)))
                .andExpect(jsonPath("$.size", is(2)))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.totalPages", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].role", everyItem(is("TECHNICIAN"))))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("ACTIVE"))))
                .andExpect(jsonPath("$.content[*].email", not(hasItem("auth-assign-pagination-inactive@fieldops.local"))));
    }

    @Test
    void technicianCannotListAssignableUsers() throws Exception {
        User technician = testUsers.create("auth-assign-forbidden@fieldops.local", Role.TECHNICIAN);
        String token = login(technician.getEmail());

        mockMvc.perform(get("/api/v1/users/assignable").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void technicianCannotScheduleInspection() throws Exception {
        User technician = testUsers.create("auth-schedule-forbidden@fieldops.local", Role.TECHNICIAN);
        String token = login(technician.getEmail());

        mockMvc.perform(post("/api/v1/inspections")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"templateId":"%s","clientId":"%s","siteId":"%s","technicianId":"%s","supervisorId":"%s",
                                 "title":"Unauthorized schedule attempt","scheduledFor":"2030-01-01T00:00:00Z"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
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

    @Test
    void forgotPasswordHasSameGenericResponseForKnownAndUnknownEmail() throws Exception {
        testUsers.create("reset-known@fieldops.local", Role.ADMIN);
        String known = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content("{\"email\":\"RESET-KNOWN@fieldops.local\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content("{\"email\":\"missing@fieldops.local\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(known, unknown);
        org.mockito.Mockito.verify(emailService).sendPasswordResetEmail(
                org.mockito.ArgumentMatchers.eq("reset-known@fieldops.local"), org.mockito.ArgumentMatchers.contains("token="));
    }

    @Test
    void resetPasswordConsumesTokenAndRevokesRefreshSession() throws Exception {
        User user = testUsers.create("reset-flow@fieldops.local", Role.ADMIN);
        String loginBody = mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"email\":\"reset-flow@fieldops.local\",\"password\":\"%s\"}".formatted(TestUsers.RAW_PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String refresh = objectMapper.readTree(loginBody).get("refreshToken").asText();
        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType("application/json")
                        .content("{\"email\":\"reset-flow@fieldops.local\"}"))
                .andExpect(status().isAccepted());
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(emailService).sendPasswordResetEmail(org.mockito.ArgumentMatchers.eq(user.getEmail()), url.capture());
        String token = url.getValue().substring(url.getValue().indexOf("token=") + 6);
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType("application/json")
                        .content("{\"token\":\"%s\",\"newPassword\":\"NewPassword@123\"}".formatted(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                        .content("{\"refreshToken\":\"%s\"}".formatted(refresh)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType("application/json")
                        .content("{\"token\":\"%s\",\"newPassword\":\"AnotherPassword@123\"}".formatted(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void temporaryPasswordIsReportedAndBlocksNormalResourcesUntilChanged() throws Exception {
        User user = testUsers.create("temporary@fieldops.local", Role.ADMIN);
        user.setMustChangePassword(true);
        userRepository.save(user);
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"email\":\"temporary@fieldops.local\",\"password\":\"%s\"}".formatted(TestUsers.RAW_PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = objectMapper.readTree(body).get("accessToken").asText();
        org.junit.jupiter.api.Assertions.assertTrue(objectMapper.readTree(body).get("mustChangePassword").asBoolean());
        mockMvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + access)
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"ChangedPassword@123\"}".formatted(TestUsers.RAW_PASSWORD)))
                .andExpect(status().isNoContent());
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
