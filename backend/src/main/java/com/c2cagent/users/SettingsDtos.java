package com.c2cagent.users;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

public final class SettingsDtos {
    private SettingsDtos() {}

    public record SettingsResponse(AutomationMode automationMode, boolean emergencyStop, boolean outboundDryRun,
                                   int sessionTimeoutMinutes, int gmailPollingIntervalMinutes,
                                   boolean externalAiConsent, String theme, boolean sourceDiscoveryMode,
                                   int gmailBackfillDays, long version, Instant updatedAt) {
        static SettingsResponse from(WorkspaceSettings s) {
            return new SettingsResponse(s.getAutomationMode(), s.isEmergencyStop(), s.isOutboundDryRun(),
                    s.getSessionTimeoutMinutes(), s.getGmailPollingIntervalMinutes(), s.isExternalAiConsent(),
                    s.getTheme(), s.isSourceDiscoveryMode(), s.getGmailBackfillDays(), s.getVersion() == null ? 0 : s.getVersion(), s.getUpdatedAt());
        }
    }

    /** General preferences. Automation mode and the emergency stop have dedicated, audited endpoints. */
    public record UpdateSettingsRequest(
            @NotNull @Min(5) @Max(1440) Integer sessionTimeoutMinutes,
            @NotNull @Min(1) @Max(1440) Integer gmailPollingIntervalMinutes,
            @NotNull Boolean externalAiConsent,
            @NotNull @Pattern(regexp = "system|light|dark") String theme,
            Boolean sourceDiscoveryMode,              // optional: null = unchanged
            @Min(1) @Max(365) Integer gmailBackfillDays) {}

    public record EmergencyStopRequest(@NotNull Boolean engaged, String reason) {}
}
