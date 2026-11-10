package com.c2cagent.users;

import com.c2cagent.audit.AuditService;
import com.c2cagent.auth.CurrentUser;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {
    private final WorkspaceSettingsRepository repository;
    private final AuditService audit;

    public SettingsService(WorkspaceSettingsRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional
    public WorkspaceSettings getOrCreate(UUID workspaceId) {
        return repository.findById(workspaceId).orElseGet(() -> repository.save(new WorkspaceSettings(workspaceId)));
    }

    @Transactional
    public WorkspaceSettings update(CurrentUser user, SettingsDtos.UpdateSettingsRequest req) {
        WorkspaceSettings s = getOrCreate(user.workspaceId());
        s.setSessionTimeoutMinutes(req.sessionTimeoutMinutes());
        s.setGmailPollingIntervalMinutes(req.gmailPollingIntervalMinutes());
        if (s.isExternalAiConsent() != req.externalAiConsent()) {
            audit.recordUser(user, "SETTINGS_EXTERNAL_AI_CONSENT_CHANGED", "workspace_settings",
                    user.workspaceId().toString(),
                    req.externalAiConsent() ? "Consent to send data to external AI providers granted"
                            : "Consent to send data to external AI providers revoked",
                    Map.of("externalAiConsent", req.externalAiConsent()));
        }
        s.setExternalAiConsent(req.externalAiConsent());
        s.setTheme(req.theme());
        audit.recordUser(user, "SETTINGS_UPDATED", "workspace_settings", user.workspaceId().toString(),
                "General settings updated", Map.of("sessionTimeoutMinutes", req.sessionTimeoutMinutes(),
                        "gmailPollingIntervalMinutes", req.gmailPollingIntervalMinutes(), "theme", req.theme()));
        return repository.saveAndFlush(s);
    }

    /** Global emergency stop: when engaged, nothing may be sent automatically or manually. */
    @Transactional
    public WorkspaceSettings setEmergencyStop(CurrentUser user, boolean engaged, String reason) {
        WorkspaceSettings s = getOrCreate(user.workspaceId());
        s.setEmergencyStop(engaged);
        String safeReason = reason == null ? "" : reason.strip();
        if (safeReason.length() > 300) {
            safeReason = safeReason.substring(0, 300);
        }
        audit.recordUser(user, engaged ? "EMERGENCY_STOP_ENGAGED" : "EMERGENCY_STOP_RELEASED",
                "workspace_settings", user.workspaceId().toString(),
                engaged ? "Emergency stop engaged: all outbound sending blocked" : "Emergency stop released",
                Map.of("reason", safeReason));
        return repository.saveAndFlush(s);
    }
}
