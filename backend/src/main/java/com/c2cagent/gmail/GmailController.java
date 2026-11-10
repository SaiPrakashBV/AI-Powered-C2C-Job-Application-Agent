package com.c2cagent.gmail;

import com.c2cagent.audit.AuditService;
import com.c2cagent.auth.CurrentUser;
import com.c2cagent.common.ApiException;
import com.c2cagent.configuration.SecretCipher;
import com.c2cagent.scheduling.BackgroundJobController;
import com.c2cagent.scheduling.BackgroundJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gmail")
@Tag(name = "Gmail accounts")
public class GmailController {
    private final GmailClientConfig clientConfig;
    private final GmailProperties props;
    private final GmailOAuthService oauth;
    private final GmailAccountRepository accounts;
    private final GmailSyncCheckpointRepository checkpoints;
    private final GmailTokenService tokens;
    private final SecretCipher cipher;
    private final BackgroundJobService jobs;
    private final AuditService audit;

    public GmailController(GmailClientConfig clientConfig, GmailProperties props, GmailOAuthService oauth,
                           GmailAccountRepository accounts, GmailSyncCheckpointRepository checkpoints,
                           GmailTokenService tokens, SecretCipher cipher, BackgroundJobService jobs, AuditService audit) {
        this.clientConfig = clientConfig;
        this.props = props;
        this.oauth = oauth;
        this.accounts = accounts;
        this.checkpoints = checkpoints;
        this.tokens = tokens;
        this.cipher = cipher;
        this.jobs = jobs;
        this.audit = audit;
    }

    public record OAuthStatus(boolean oauthConfigured, String configSource, String redirectUri, List<String> scopes) {}

    public record AccountDto(UUID id, String emailAddress, GmailAccountStatus status, boolean syncEnabled,
                             Instant lastSyncAt, String lastSyncStatus, String lastError, Instant connectedAt,
                             long messagesSynced, Instant backfillCompletedAt, BigInteger historyId,
                             String grantedScopes) {}

    public record StartResponse(String authorizationUrl) {}

    public record SyncToggleRequest(@NotNull Boolean enabled) {}

    @GetMapping("/oauth/status")
    public OAuthStatus status(CurrentUser user) {
        var c = clientConfig.client();
        return new OAuthStatus(c.isPresent(), c.map(GmailClientConfig.Client::source).orElse(null),
                clientConfig.redirectUri(), List.of(props.scopes().split("\\s+")));
    }

    @GetMapping("/accounts")
    public List<AccountDto> list(CurrentUser user) {
        return accounts.findByWorkspaceIdOrderByCreatedAtAsc(user.workspaceId()).stream().map(this::toDto).toList();
    }

    @PostMapping("/oauth/start")
    @Operation(summary = "Begin connecting a Gmail account; the browser is sent to Google's consent page")
    public StartResponse start(CurrentUser user, @RequestParam(required = false) String returnTo) {
        return new StartResponse(oauth.start(user, returnTo));
    }

    @GetMapping("/oauth/callback")
    @Operation(summary = "OAuth loopback redirect target (called by the browser after Google consent)")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        var outcome = oauth.callback(code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(outcome.redirectUrl())).build();
    }

    @PostMapping("/accounts/{id}/sync")
    @Operation(summary = "Queue an immediate synchronization")
    public BackgroundJobController.JobDto syncNow(CurrentUser user, @PathVariable UUID id) {
        GmailAccount a = find(user, id);
        if (a.getStatus() != GmailAccountStatus.CONNECTED) {
            throw ApiException.conflict("GMAIL_NOT_CONNECTED", "Reconnect this Gmail account before syncing");
        }
        var job = jobs.enqueue(user.workspaceId(), GmailSyncJobHandler.TYPE, GmailSyncJobHandler.payload(id),
                GmailSyncJobHandler.dedupKey(id), 5);
        return new BackgroundJobController.JobDto(job.getId(), job.getJobType(), job.getStatus(), job.getAttempts(),
                job.getMaxAttempts(), job.getRunAfter(), job.getLastError(), job.getFailureKind(), job.getCreatedAt(),
                job.getCompletedAt());
    }

    @PostMapping("/accounts/{id}/sync-enabled")
    @Transactional
    public AccountDto setSyncEnabled(CurrentUser user, @PathVariable UUID id, @RequestBody SyncToggleRequest req) {
        GmailAccount a = find(user, id);
        a.setSyncEnabled(req.enabled() && a.getStatus() == GmailAccountStatus.CONNECTED);
        accounts.save(a);
        audit.recordUser(user, req.enabled() ? "GMAIL_SYNC_RESUMED" : "GMAIL_SYNC_PAUSED", "gmail_account",
                id.toString(), (req.enabled() ? "Sync resumed for " : "Sync paused for ") + a.getEmailAddress(), Map.of());
        return toDto(a);
    }

    @PostMapping("/accounts/{id}/disconnect")
    @Transactional
    @Operation(summary = "Revoke Google access and delete stored tokens (synced emails are kept)")
    public AccountDto disconnect(CurrentUser user, @PathVariable UUID id) {
        GmailAccount a = find(user, id);
        boolean revoked = false;
        if (a.getRefreshTokenEncrypted() != null) {
            revoked = tokens.revoke(cipher.decrypt(a.getRefreshTokenEncrypted()));
        }
        a.disconnect();
        accounts.save(a);
        audit.recordUser(user, "GMAIL_DISCONNECTED", "gmail_account", id.toString(),
                "Gmail account disconnected: " + a.getEmailAddress(), Map.of("revokedAtGoogle", revoked));
        return toDto(a);
    }

    private GmailAccount find(CurrentUser user, UUID id) {
        return accounts.findByIdAndWorkspaceId(id, user.workspaceId()).orElseThrow(() -> ApiException.notFound("Gmail account"));
    }

    private AccountDto toDto(GmailAccount a) {
        var cp = checkpoints.findById(a.getId());
        return new AccountDto(a.getId(), a.getEmailAddress(), a.getStatus(), a.isSyncEnabled(), a.getLastSyncAt(),
                a.getLastSyncStatus(), a.getLastError(), a.getConnectedAt(),
                cp.map(GmailSyncCheckpoint::getMessagesSynced).orElse(0L),
                cp.map(GmailSyncCheckpoint::getBackfillCompletedAt).orElse(null),
                cp.map(GmailSyncCheckpoint::getHistoryId).orElse(null), a.getGrantedScopes());
    }
}
