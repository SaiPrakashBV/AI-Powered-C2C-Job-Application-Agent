package com.c2cagent.gmail;

import com.c2cagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A connected mailbox. Tokens are stored encrypted (see SecretCipher); the Gmail password is never handled. */
@Entity
@Table(name = "gmail_account")
public class GmailAccount extends BaseEntity {
    @Column(name = "workspace_id", nullable = false) private UUID workspaceId;
    @Column(name = "email_address", nullable = false, length = 320) private String emailAddress;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30) private GmailAccountStatus status;
    @Column(name = "granted_scopes", length = 1000) private String grantedScopes;
    @Column(name = "refresh_token_encrypted") private String refreshTokenEncrypted;
    @Column(name = "access_token_encrypted") private String accessTokenEncrypted;
    @Column(name = "access_token_expires_at") private Instant accessTokenExpiresAt;
    @Column(name = "sync_enabled", nullable = false) private boolean syncEnabled = true;
    @Column(name = "last_sync_at") private Instant lastSyncAt;
    @Column(name = "last_sync_status", length = 30) private String lastSyncStatus;
    @Column(name = "last_error", length = 1000) private String lastError;
    @Column(name = "connected_at") private Instant connectedAt;

    protected GmailAccount() {}

    public GmailAccount(UUID workspaceId, String emailAddress) {
        this.workspaceId = workspaceId;
        this.emailAddress = emailAddress;
        this.status = GmailAccountStatus.DISCONNECTED;
    }

    public void connect(String refreshTokenEnc, String accessTokenEnc, Instant expiresAt, String scopes, Instant now) {
        this.refreshTokenEncrypted = refreshTokenEnc;
        this.accessTokenEncrypted = accessTokenEnc;
        this.accessTokenExpiresAt = expiresAt;
        this.grantedScopes = scopes;
        this.status = GmailAccountStatus.CONNECTED;
        this.syncEnabled = true;
        this.connectedAt = now;
        this.lastError = null;
    }

    public void disconnect() {
        this.refreshTokenEncrypted = null;
        this.accessTokenEncrypted = null;
        this.accessTokenExpiresAt = null;
        this.status = GmailAccountStatus.DISCONNECTED;
        this.syncEnabled = false;
    }

    public void requireReauth(String reason) {
        this.status = GmailAccountStatus.REAUTH_REQUIRED;
        this.accessTokenEncrypted = null;
        this.accessTokenExpiresAt = null;
        this.lastError = truncate(reason);
        this.lastSyncStatus = "REAUTH_REQUIRED";
    }

    public void recordSync(String status, String error, Instant at) {
        this.lastSyncStatus = status;
        this.lastError = truncate(error);
        this.lastSyncAt = at;
    }

    private static String truncate(String s) { return s == null ? null : (s.length() > 1000 ? s.substring(0, 1000) : s); }

    public UUID getWorkspaceId() { return workspaceId; }
    public String getEmailAddress() { return emailAddress; }
    public GmailAccountStatus getStatus() { return status; }
    public String getGrantedScopes() { return grantedScopes; }
    public String getRefreshTokenEncrypted() { return refreshTokenEncrypted; }
    public String getAccessTokenEncrypted() { return accessTokenEncrypted; }
    public Instant getAccessTokenExpiresAt() { return accessTokenExpiresAt; }
    public void setAccessToken(String enc, Instant expiresAt) { accessTokenEncrypted = enc; accessTokenExpiresAt = expiresAt; }
    public boolean isSyncEnabled() { return syncEnabled; }
    public void setSyncEnabled(boolean v) { syncEnabled = v; }
    public Instant getLastSyncAt() { return lastSyncAt; }
    public String getLastSyncStatus() { return lastSyncStatus; }
    public String getLastError() { return lastError; }
    public Instant getConnectedAt() { return connectedAt; }
}
