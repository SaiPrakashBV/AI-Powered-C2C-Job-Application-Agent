package com.c2cagent.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c2cagent.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SettingsIT extends IntegrationTestBase {

    @Test
    void defaultsAreSafe() throws Exception {
        AppUser owner = createOwner("owner");
        mvc.perform(get("/api/v1/settings").with(as(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.automationMode").value("MANUAL_APPROVAL"))
                .andExpect(jsonPath("$.emergencyStop").value(false))
                .andExpect(jsonPath("$.outboundDryRun").value(true))
                .andExpect(jsonPath("$.externalAiConsent").value(false));
    }

    @Test
    void emergencyStopIsPersistedAndAudited() throws Exception {
        AppUser owner = createOwner("owner");
        mvc.perform(post("/api/v1/settings/emergency-stop").with(as(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"engaged\":true,\"reason\":\"testing\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emergencyStop").value(true));
        mvc.perform(get("/api/v1/settings").with(as(owner))).andExpect(jsonPath("$.emergencyStop").value(true));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_event WHERE event_type='EMERGENCY_STOP_ENGAGED'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void updateValidatesInputAndCannotChangeAutomationMode() throws Exception {
        AppUser owner = createOwner("owner");
        mvc.perform(put("/api/v1/settings").with(as(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionTimeoutMinutes":2,"gmailPollingIntervalMinutes":15,
                                 "externalAiConsent":false,"theme":"dark"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sessionTimeoutMinutes"));
        // Unknown field automationMode is ignored by the general endpoint: mode stays MANUAL_APPROVAL.
        mvc.perform(put("/api/v1/settings").with(as(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionTimeoutMinutes":30,"gmailPollingIntervalMinutes":10,
                                 "externalAiConsent":true,"theme":"dark","automationMode":"FULL_AUTONOMOUS"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionTimeoutMinutes").value(30))
                .andExpect(jsonPath("$.automationMode").value("MANUAL_APPROVAL"))
                .andExpect(jsonPath("$.externalAiConsent").value(true));
    }
}
