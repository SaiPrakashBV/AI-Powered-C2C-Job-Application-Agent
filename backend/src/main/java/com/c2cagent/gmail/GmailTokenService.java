package com.c2cagent.gmail;

import com.c2cagent.configuration.SecretCipher;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** OAuth token exchange, refresh and revocation against Google's token endpoints. Tokens never reach logs. */
@Service
public class GmailTokenService {
    public record Tokens(String accessToken, String refreshToken, Instant expiresAt, String scope) {}

    private final GmailProperties props;
    private final GmailClientConfig clientConfig;
    private final GmailAccountRepository accounts;
    private final SecretCipher cipher;
    private final HttpClient http;
    private final Clock clock;

    public GmailTokenService(GmailProperties props, GmailClientConfig clientConfig, GmailAccountRepository accounts,
                             SecretCipher cipher, Optional<Clock> clock) {
        this.props = props;
        this.clientConfig = clientConfig;
        this.accounts = accounts;
        this.cipher = cipher;
        this.http = OAuthHttp.client(props.requestTimeout());
        this.clock = clock.orElse(Clock.systemUTC());
    }

    public Tokens exchangeCode(String code, String codeVerifier) {
        GmailClientConfig.Client c = requireClient();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("code_verifier", codeVerifier);
        form.put("redirect_uri", clientConfig.redirectUri());
        form.put("client_id", c.clientId());
        form.put("client_secret", c.clientSecret());
        OAuthHttp.Response r = OAuthHttp.postForm(http, props.tokenUri(), form, props.requestTimeout());
        if (r.status() != 200) {
            throw new MailboxException(r.status() >= 500 ? MailboxException.Kind.TRANSIENT : MailboxException.Kind.PERMANENT,
                    "Google rejected the authorization code (" + OAuthHttp.errorReason(r) + ")");
        }
        return toTokens(r, null);
    }

    /** Returns a valid access token, refreshing it when it expires within 60 seconds. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String accessToken(UUID accountId, boolean forceRefresh) {
        GmailAccount a = accounts.findById(accountId).orElseThrow(
                () -> new MailboxException(MailboxException.Kind.PERMANENT, "Gmail account no longer exists"));
        if (!forceRefresh && a.getAccessTokenEncrypted() != null && a.getAccessTokenExpiresAt() != null
                && a.getAccessTokenExpiresAt().isAfter(clock.instant().plusSeconds(60))) {
            return cipher.decrypt(a.getAccessTokenEncrypted());
        }
        if (a.getRefreshTokenEncrypted() == null) {
            throw new MailboxException(MailboxException.Kind.AUTH_REVOKED, "No refresh token stored; reconnect the account");
        }
        GmailClientConfig.Client c = requireClient();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", cipher.decrypt(a.getRefreshTokenEncrypted()));
        form.put("client_id", c.clientId());
        form.put("client_secret", c.clientSecret());
        OAuthHttp.Response r = OAuthHttp.postForm(http, props.tokenUri(), form, props.requestTimeout());
        if (r.status() == 400 || r.status() == 401) {
            String reason = OAuthHttp.errorReason(r);
            if ("invalid_grant".equals(reason) || "unauthorized_client".equals(reason) || r.status() == 401) {
                throw new MailboxException(MailboxException.Kind.AUTH_REVOKED,
                        "Google access was revoked or expired (" + reason + "); reconnect the account");
            }
            throw new MailboxException(MailboxException.Kind.PERMANENT, "Token refresh rejected (" + reason + ")");
        }
        if (r.status() != 200) {
            throw new MailboxException(MailboxException.Kind.TRANSIENT, "Token refresh failed (HTTP " + r.status() + ")");
        }
        Tokens t = toTokens(r, null);
        a.setAccessToken(cipher.encrypt(t.accessToken()), t.expiresAt());
        accounts.save(a);
        return t.accessToken();
    }

    /** Best-effort revocation at Google; local tokens are deleted regardless. */
    public boolean revoke(String refreshToken) {
        try {
            OAuthHttp.Response r = OAuthHttp.postForm(http, props.revokeUri(), Map.of("token", refreshToken),
                    props.requestTimeout());
            return r.status() == 200;
        } catch (MailboxException e) {
            return false;
        }
    }

    private Tokens toTokens(OAuthHttp.Response r, String existingRefresh) {
        Object at = r.body().get("access_token");
        if (at == null) {
            throw new MailboxException(MailboxException.Kind.PERMANENT, "Token response did not contain an access token");
        }
        Object exp = r.body().get("expires_in");
        long seconds = exp instanceof Number n ? n.longValue() : 3600;
        Object rt = r.body().get("refresh_token");
        Object scope = r.body().get("scope");
        return new Tokens(at.toString(), rt == null ? existingRefresh : rt.toString(),
                clock.instant().plusSeconds(seconds), scope == null ? null : scope.toString());
    }

    private GmailClientConfig.Client requireClient() {
        return clientConfig.client().orElseThrow(() -> new MailboxException(MailboxException.Kind.PERMANENT,
                "Google OAuth client is not configured (see GMAIL_OAUTH_SETUP.md)"));
    }
}
