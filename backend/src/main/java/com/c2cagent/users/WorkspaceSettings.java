package com.c2cagent.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Per-workspace settings. Safety-critical values are typed columns so they can be enforced server-side. */
@Entity
@Table(name = "workspace_settings")
public class WorkspaceSettings {
    @Id
    @Column(name = "workspace_id")
    private UUID workspaceId;
    @Enumerated(EnumType.STRING)
    @Column(name = "automation_mode", nullable = false, length = 30)
    private AutomationMode automationMode = AutomationMode.MANUAL_APPROVAL;
    @Column(name = "emergency_stop", nullable = false)
    private boolean emergencyStop;
    @Column(name = "outbound_dry_run", nullable = false)
    private boolean outboundDryRun = true;
    @Column(name = "session_timeout_minutes", nullable = false)
    private int sessionTimeoutMinutes = 60;
    @Column(name = "gmail_polling_interval_minutes", nullable = false)
    private int gmailPollingIntervalMinutes = 15;
    @Column(name = "external_ai_consent", nullable = false)
    private boolean externalAiConsent;
    @Column(nullable = false, length = 10)
    private String theme = "system";
    @Column(name = "source_discovery_mode", nullable = false)
    private boolean sourceDiscoveryMode;
    @Column(name = "gmail_backfill_days", nullable = false)
    private int gmailBackfillDays = 14;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    protected WorkspaceSettings() {}

    public WorkspaceSettings(UUID workspaceId) { this.workspaceId = workspaceId; }

    public UUID getWorkspaceId() { return workspaceId; }
    public AutomationMode getAutomationMode() { return automationMode; }
    public void setAutomationMode(AutomationMode automationMode) { this.automationMode = automationMode; }
    public boolean isEmergencyStop() { return emergencyStop; }
    public void setEmergencyStop(boolean emergencyStop) { this.emergencyStop = emergencyStop; }
    public boolean isOutboundDryRun() { return outboundDryRun; }
    public void setOutboundDryRun(boolean outboundDryRun) { this.outboundDryRun = outboundDryRun; }
    public int getSessionTimeoutMinutes() { return sessionTimeoutMinutes; }
    public void setSessionTimeoutMinutes(int v) { this.sessionTimeoutMinutes = v; }
    public int getGmailPollingIntervalMinutes() { return gmailPollingIntervalMinutes; }
    public void setGmailPollingIntervalMinutes(int v) { this.gmailPollingIntervalMinutes = v; }
    public boolean isExternalAiConsent() { return externalAiConsent; }
    public void setExternalAiConsent(boolean externalAiConsent) { this.externalAiConsent = externalAiConsent; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public boolean isSourceDiscoveryMode() { return sourceDiscoveryMode; }
    public void setSourceDiscoveryMode(boolean v) { this.sourceDiscoveryMode = v; }
    public int getGmailBackfillDays() { return gmailBackfillDays; }
    public void setGmailBackfillDays(int v) { this.gmailBackfillDays = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
