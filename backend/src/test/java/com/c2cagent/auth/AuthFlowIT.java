package com.c2cagent.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c2cagent.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class AuthFlowIT extends IntegrationTestBase {

    private static String setupJson(String username, String password) {
        return """
                {"username":"%s","displayName":"Sai","email":"owner@example.com","password":"%s"}
                """.formatted(username, password);
    }

    @Test
    void firstTimeSetupCreatesOwnerAndAuthenticatesSession() throws Exception {
        mvc.perform(get("/api/v1/auth/setup-status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(true));

        var result = mvc.perform(post("/api/v1/auth/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(setupJson("owner", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("owner"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getMaxInactiveInterval()).isEqualTo(60 * 60);

        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("owner"));
        mvc.perform(get("/api/v1/auth/setup-status")).andExpect(jsonPath("$.setupRequired").value(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE event_type='SETUP_COMPLETED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void setupIsOnlyAllowedOnce() throws Exception {
        createOwner("owner");
        mvc.perform(post("/api/v1/auth/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(setupJson("intruder", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETUP_ALREADY_COMPLETED"));
    }

    @Test
    void setupRejectsWeakPasswordAndInvalidInput() throws Exception {
        mvc.perform(post("/api/v1/auth/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(setupJson("owner", "short1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        mvc.perform(post("/api/v1/auth/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(setupJson("bad user!", PASSWORD)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("username"));
        mvc.perform(get("/api/v1/auth/setup-status")).andExpect(jsonPath("$.setupRequired").value(true));
    }

    @Test
    void stateChangingRequestsRequireCsrfToken() throws Exception {
        mvc.perform(post("/api/v1/auth/setup").contentType(MediaType.APPLICATION_JSON)
                        .content(setupJson("owner", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedApisRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/dashboard/summary")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
        mvc.perform(get("/api/v1/settings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void loginSucceedsWithCorrectCredentialsAndLogoutInvalidatesSession() throws Exception {
        createOwner("owner");
        var result = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"OWNER\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("owner")).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/v1/dashboard/summary").session(session)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/logout").with(csrf()).session(session)).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejectedWithGenericMessageAndAccountLocksAfterRepeatedFailures() throws Exception {
        createOwner("owner");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"owner\",\"password\":\"wrong-password-1\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owner\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"whatever-123\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE event_type='LOGIN_FAILED'",
                Integer.class)).isEqualTo(6);
    }

    @Test
    void passwordHashesAreNotPlaintext() {
        createOwner("owner");
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user", String.class);
        assertThat(hash).startsWith("{bcrypt}").doesNotContain(PASSWORD);
    }
}
