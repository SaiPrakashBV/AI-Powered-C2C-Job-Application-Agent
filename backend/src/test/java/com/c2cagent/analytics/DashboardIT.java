package com.c2cagent.analytics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c2cagent.IntegrationTestBase;
import com.c2cagent.users.AppUser;
import org.junit.jupiter.api.Test;

class DashboardIT extends IntegrationTestBase {
    @Test
    void summaryReportsRealSystemStateAndModuleAvailability() throws Exception {
        AppUser owner = createOwner("owner");
        mvc.perform(get("/api/v1/dashboard/summary").with(as(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Test owner"))
                .andExpect(jsonPath("$.safety.automationMode").value("MANUAL_APPROVAL"))
                .andExpect(jsonPath("$.safety.outboundDryRun").value(true))
                .andExpect(jsonPath("$.system.database").value("UP"))
                .andExpect(jsonPath("$.system.schemaVersion").value("1"))
                .andExpect(jsonPath("$.jobCounts.QUEUED").value(0))
                .andExpect(jsonPath("$.recentActivity[0].eventType").value("SETUP_COMPLETED"))
                .andExpect(jsonPath("$.modules[?(@.key=='gmail')].availability").value("PLANNED"));
    }

    @Test
    void diagnosticsReportsEnvironment() throws Exception {
        AppUser owner = createOwner("owner");
        mvc.perform(get("/api/v1/diagnostics").with(as(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.dataDirectoryWritable").value(true))
                .andExpect(jsonPath("$.schemaVersion").value("1"));
    }

    @Test
    void spaRoutesForwardToIndexButStaticAssetsAndApisDoNot() throws Exception {
        mvc.perform(get("/resumes")).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/jobs/123/history")).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/assets/index-abc.js")).andExpect(forwardedUrl(null));
        mvc.perform(get("/api/v1/unknown")).andExpect(status().isUnauthorized());
    }
}
