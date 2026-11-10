package com.c2cagent.email;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c2cagent.IntegrationTestBase;
import com.c2cagent.users.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SourceRuleApiIT extends IntegrationTestBase {

    @Test
    void crudValidationEvaluateAndAudit() throws Exception {
        AppUser owner = createOwner("owner");
        String created = mvc.perform(post("/api/v1/source-rules").with(as(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleType\":\"ALLOW_DOMAIN\",\"matchValue\":\"@Staffing-Company.com\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.matchValue").value("staffing-company.com"))
                .andReturn().getResponse().getContentAsString();
        String id = created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mvc.perform(post("/api/v1/source-rules").with(as(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleType\":\"ALLOW_DOMAIN\",\"matchValue\":\"staffing-company.com\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_RULE"));
        mvc.perform(post("/api/v1/source-rules").with(as(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleType\":\"ALLOW_ADDRESS\",\"matchValue\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RULE_VALUE"));

        mvc.perform(post("/api/v1/source-rules/evaluate").with(as(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAddress\":\"Recruiter <jobs@staffing-company.com>\"}"))
                .andExpect(jsonPath("$.decision").value("ALLOWED"))
                .andExpect(jsonPath("$.continuesToClassification").value(true));

        mvc.perform(put("/api/v1/source-rules/" + id).with(as(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleType\":\"ALLOW_DOMAIN\",\"matchValue\":\"staffing-company.com\",\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/v1/source-rules/evaluate").with(as(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAddress\":\"jobs@staffing-company.com\"}"))
                .andExpect(jsonPath("$.decision").value("UNLISTED"));

        mvc.perform(delete("/api/v1/source-rules/" + id).with(as(owner)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/source-rules").with(as(owner))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/audit-events?eventType=SOURCE_RULE_CREATED").with(as(owner)))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void importExportRoundTripSkipsDuplicatesAndReportsErrors() throws Exception {
        AppUser owner = createOwner("owner");
        mvc.perform(post("/api/v1/source-rules/import").with(as(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rules":[{"ruleType":"ALLOW_ADDRESS","matchValue":"recruiter@vendor-a.com"},
                                          {"ruleType":"ALLOW_ADDRESS","matchValue":"jobs@vendor-b.com","priority":10},
                                          {"ruleType":"BLOCK_ADDRESS","matchValue":"newsletters@example.com"},
                                          {"ruleType":"ALLOW_ADDRESS","matchValue":"recruiter@vendor-a.com"},
                                          {"ruleType":"BLOCK_DOMAIN","matchValue":"not a domain"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.skippedDuplicates").value(1))
                .andExpect(jsonPath("$.errors.length()").value(1));
        mvc.perform(get("/api/v1/source-rules/export").with(as(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].matchValue").value("jobs@vendor-b.com"));
    }

    @Test
    void discoveryModeSettingChangesUnknownSenderDecision() throws Exception {
        AppUser owner = createOwner("owner");
        mvc.perform(put("/api/v1/settings").with(as(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionTimeoutMinutes":60,"gmailPollingIntervalMinutes":15,"externalAiConsent":false,
                                 "theme":"system","sourceDiscoveryMode":true,"gmailBackfillDays":30}"""))
                .andExpect(jsonPath("$.sourceDiscoveryMode").value(true)).andExpect(jsonPath("$.gmailBackfillDays").value(30));
        mvc.perform(post("/api/v1/source-rules/evaluate").with(as(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAddress\":\"new.recruiter@unknown-vendor.com\"}"))
                .andExpect(jsonPath("$.decision").value("DISCOVERY"));
    }
}
