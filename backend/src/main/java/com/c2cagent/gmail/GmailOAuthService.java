package com.c2cagent.gmail;

import com.c2cagent.audit.AuditService;
import com.c2cagent.auth.CurrentUser;
import com.c2cagent.common.ApiException;
import com.c2cagent.configuration.SecretCipher;
import com.c2cagent.scheduling.BackgroundJobService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Google OAuth 2.0 for installed apps: authorization code + PKCE (S256) with a loopback redirect to this local
 * backend. The {@code state} value (single use, 10-minute lifetime) binds the callback to the user who started it,
 * so the callback does not depend on the session cookie (which is SameSite=Strict).
 */
@Service
public class GmailOAuthService {
    private static final Duration PENDING_TTL = Duration.ofMinutes(10);
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final GmailProperties props;
    private final GmailClientConfig clientConfig;
    private final GmailTokenService tokens;
    private final GmailRestClientFactory restClients;
    private final GmailAccountRepository accounts;
    private final GmailSyncCheckpointRepository checkpoints;
    private final SecretCipher cipher;
    private final BackgroundJobService jobs;
    private final AuditService audit;
    private final Clock clock;

    record Pending(CurrentUser user, String verifier, String returnBase, Instant createdAt) {}

    public record CallbackOutcome(String redirectUrl, boolean success) {}

    public GmailOAuthService(GmailProperties props, GmailClientConfig clientConfig, GmailTokenService tokens,
                             GmailRestClientFactory restClients, GmailAccountRepository accounts,
                             GmailSyncCheckpointRepository checkpoints, SecretCipher cipher, BackgroundJobService jobs,
                             AuditService audit, Optional<Clock> clock) {
        this.props = props;
        this.clientConfig = clientConfig;
        this.tokens = tokens;
        this.restClients = restClients;
        this.accounts = accounts;
        this.checkpoints = checkpoints;
        this.cipher = cipher;
        this.jobs = jobs;
        this.audit = audit;
        this.clock = clock.orElse(Clock.systemUTC());
    }

    public String start(CurrentUser user, String returnTo) {
        GmailClientConfig.Client client = clientConfig.client().orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                "GMAIL_OAUTH_NOT_CONFIGURED", "Google OAuth client is not configured. See GMAIL_OAUTH_SETUP.md."));
        purgeExpired();
        String verifier = randomToken(64);
        String state = randomToken(32);
        pending.put(state, new Pending(user, verifier, safeReturnBase(returnTo), clock.instant()));
        return props.authUri() + "?response_type=code"
                + "&client_id=" + OAuthHttp.enc(client.clientId())
                + "&redirect_uri=" + OAuthHttp.enc(clientConfig.redirectUri())
                + "&scope=" + OAuthHttp.enc(props.scopes())
                + "&code_challenge=" + challenge(verifier)
                + "&code_challenge_method=S256"
                + "&state=" + state
                + "&access_type=offline&prompt=consent&include_granted_scopes=true";
    }

    @Transactional
    public CallbackOutcome callback(String code, String state, String error) {
        Pending p = state == null ? null : pending.remove(state);
        if (p == null || p.createdAt().isBefore(clock.instant().minus(PENDING_TTL))) {
            return new CallbackOutcome("/gmail?oauthError=invalid_state", false);
        }
        String base = p.returnBase() + "/gmail";
        if (error != null) {
            return new CallbackOutcome(base + "?oauthError=" + OAuthHttp.enc(error), false);
        }
        if (code == null || code.isBlank()) {
            return new CallbackOutcome(base + "?oauthError=missing_code", false);
        }
        try {
            GmailTokenService.Tokens t = tokens.exchangeCode(code, p.verifier());
            if (t.refreshToken() == null) {
                return new CallbackOutcome(base + "?oauthError=no_refresh_token", false);
            }
            if (t.scope() != null && !t.scope().contains("gmail.readonly")) {
                return new CallbackOutcome(base + "?oauthError=insufficient_scope", false);
            }
            String email = restClients.withAccessToken(t.accessToken()).profile().emailAddress();

            {
                GmailAccount account = accounts.findByWorkspaceAndEmail(p.user().workspaceId(), email)
                        .orElseGet(() -> new GmailAccount(p.user().workspaceId(), email.toLowerCase()));
                account.connect(cipher.encrypt(t.refreshToken()), cipher.encrypt(t.accessToken()), t.expiresAt(),
                        t.scope(), clock.instant());
                accounts.save(account);
                if (checkpoints.findById(account.getId()).isEmpty()) {
                    checkpoints.save(new GmailSyncCheckpoint(account.getId()));
                }
                audit.recordUser(p.user(), "GMAIL_CONNECTED", "gmail_account", account.getId().toString(),
                        "Gmail account connected: " + email, Map.of("scopes", t.scope() == null ? "" : t.scope()));
                jobs.enqueue(account.getWorkspaceId(), GmailSyncJobHandler.TYPE,
                        GmailSyncJobHandler.payload(account.getId()), GmailSyncJobHandler.dedupKey(account.getId()), 5);
            }
            return new CallbackOutcome(base + "?connected=" + OAuthHttp.enc(email), true);
        } catch (MailboxException e) {
            return new CallbackOutcome(base + "?oauthError=" + OAuthHttp.enc(e.kind().name().toLowerCase()), false);
        }
    }

    /** Only loopback origins are accepted as the post-login return address (prevents open redirects). */
    static String safeReturnBase(String returnTo) {
        try {
            if (returnTo != null) {
                URI u = URI.create(returnTo);
                if ("http".equals(u.getScheme()) && ("localhost".equals(u.getHost()) || "127.0.0.1".equals(u.getHost()))) {
                    return "http://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
                }
            }
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        return "";
    }

    private void purgeExpired() {
        Instant cutoff = clock.instant().minus(PENDING_TTL);
        pending.values().removeIf(p -> p.createdAt().isBefore(cutoff));
    }

    private String randomToken(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String challenge(String verifier) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
