package com.c2cagent.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c2cagent.IntegrationTestBase;
import com.c2cagent.configuration.SecretCipher;
import com.c2cagent.fixtures.StubGoogleServer;
import com.c2cagent.scheduling.BackgroundJobRepository;
import com.c2cagent.users.AppUser;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

class GmailOAuthIT extends IntegrationTestBase {
    static final StubGoogleServer GOOGLE = new StubGoogleServer();

    @DynamicPropertySource
    static void google(DynamicPropertyRegistry r) {
        r.add("app.gmail.client-id", () -> "test-client.apps.googleusercontent.com");
        r.add("app.gmail.client-secret", () -> "test-secret");
        r.add("app.gmail.auth-uri", () -> GOOGLE.baseUrl() + "/o/oauth2/v2/auth");
        r.add("app.gmail.token-uri", () -> GOOGLE.baseUrl() + "/token");
        r.add("app.gmail.revoke-uri", () -> GOOGLE.baseUrl() + "/revoke");
        r.add("app.gmail.api-base-url", GOOGLE::baseUrl);
        r.add("app.gmail.redirect-uri", () -> "http://127.0.0.1:8080/api/v1/gmail/oauth/callback");
    }

    @Autowired GmailAccountRepository accounts;
    @Autowired BackgroundJobRepository jobs;
    @Autowired SecretCipher cipher;
    @Autowired GmailTokenService tokenService;
    AppUser owner;

    @BeforeEach
    void setUp() {
        GOOGLE.requests.clear();
        GOOGLE.responses.clear();
        owner = createOwner("owner");
    }

    private Map<String, String> query(String url) {
        return Arrays.stream(URI.create(url).getRawQuery().split("&")).map(p -> p.split("=", 2))
                .collect(Collectors.toMap(p -> p[0], p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
    }

    private String startAndGetState() throws Exception {
        String json = mvc.perform(post("/api/v1/gmail/oauth/start?returnTo=http://localhost:8080").with(as(owner)).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url = JsonMapper.builder().build().readTree(json).get("authorizationUrl").asString();
        Map<String, String> q = query(url);
        assertThat(url).startsWith(GOOGLE.baseUrl() + "/o/oauth2/v2/auth?");
        assertThat(q).containsEntry("code_challenge_method", "S256").containsEntry("response_type", "code")
                .containsEntry("access_type", "offline")
                .containsEntry("redirect_uri", "http://127.0.0.1:8080/api/v1/gmail/oauth/callback");
        assertThat(q.get("scope")).contains("gmail.readonly").contains("gmail.compose").doesNotContain("mail.google.com");
        assertThat(q.get("code_challenge")).hasSizeGreaterThan(40);
        return q.get("state");
    }

    @Test
    void statusReportsConfiguration() throws Exception {
        mvc.perform(get("/api/v1/gmail/oauth/status").with(as(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.oauthConfigured").value(true))
                .andExpect(jsonPath("$.configSource").value("properties"));
    }

    @Test
    void fullConnectFlowStoresEncryptedTokensAndQueuesInitialSync() throws Exception {
        GOOGLE.respond("/token", 200, """
                {"access_token":"ya29.access","expires_in":3599,"refresh_token":"1//refresh",
                 "scope":"https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/gmail.compose","token_type":"Bearer"}""");
        GOOGLE.respond("/gmail/v1/users/me/profile", 200, "{\"emailAddress\":\"Candidate@Example.com\",\"historyId\":\"777\"}");
        String state = startAndGetState();

        // Callback arrives without a session cookie (SameSite=Strict); the state alone identifies the user.
        mvc.perform(get("/api/v1/gmail/oauth/callback?code=auth-code-123&state=" + state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:8080/gmail?connected=Candidate%40Example.com"));

        var tokenReq = GOOGLE.requests.stream().filter(r -> r.path().equals("/token")).findFirst().orElseThrow();
        assertThat(tokenReq.body()).contains("grant_type=authorization_code").contains("code=auth-code-123")
                .contains("code_verifier=").contains("client_secret=test-secret");
        var profileReq = GOOGLE.requests.stream().filter(r -> r.path().endsWith("/profile")).findFirst().orElseThrow();
        assertThat(profileReq.authorization()).isEqualTo("Bearer ya29.access");

        GmailAccount a = accounts.findAll().getFirst();
        assertThat(a.getStatus()).isEqualTo(GmailAccountStatus.CONNECTED);
        assertThat(a.getEmailAddress()).isEqualTo("candidate@example.com");
        assertThat(a.getRefreshTokenEncrypted()).doesNotContain("refresh");
        assertThat(cipher.decrypt(a.getRefreshTokenEncrypted())).isEqualTo("1//refresh");
        assertThat(jobs.findAll()).anyMatch(j -> j.getJobType().equals("GMAIL_SYNC"));

        // State is single-use.
        mvc.perform(get("/api/v1/gmail/oauth/callback?code=x&state=" + state))
                .andExpect(header().string("Location", "/gmail?oauthError=invalid_state"));
    }

    @Test
    void forgedStateAndUserDenialAreRejected() throws Exception {
        mvc.perform(get("/api/v1/gmail/oauth/callback?code=x&state=forged")).andExpect(status().isFound())
                .andExpect(header().string("Location", "/gmail?oauthError=invalid_state"));
        String state = startAndGetState();
        mvc.perform(get("/api/v1/gmail/oauth/callback?error=access_denied&state=" + state))
                .andExpect(header().string("Location", "http://localhost:8080/gmail?oauthError=access_denied"));
        assertThat(accounts.count()).isZero();
    }

    @Test
    void returnAddressIsRestrictedToLoopback() {
        assertThat(GmailOAuthService.safeReturnBase("http://localhost:5173")).isEqualTo("http://localhost:5173");
        assertThat(GmailOAuthService.safeReturnBase("http://evil.example")).isEmpty();
        assertThat(GmailOAuthService.safeReturnBase("javascript:alert(1)")).isEmpty();
    }

    @Test
    void refreshUsesRefreshTokenAndInvalidGrantMeansReauth() {
        GmailAccount a = new GmailAccount(owner.getWorkspaceId(), "c@example.com");
        a.connect(cipher.encrypt("1//refresh"), null, null, "gmail.readonly", java.time.Instant.now());
        a = accounts.save(a);
        GOOGLE.respond("/token", 200, "{\"access_token\":\"ya29.new\",\"expires_in\":3600}");
        assertThat(tokenService.accessToken(a.getId(), false)).isEqualTo("ya29.new");
        assertThat(GOOGLE.requests.getLast().body()).contains("grant_type=refresh_token").contains("refresh_token=1%2F%2Frefresh");
        // Cached until close to expiry: no second token call.
        int calls = GOOGLE.requests.size();
        assertThat(tokenService.accessToken(a.getId(), false)).isEqualTo("ya29.new");
        assertThat(GOOGLE.requests.size()).isEqualTo(calls);

        GOOGLE.respond("/token", 400, "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}");
        GmailAccount finalA = a;
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tokenService.accessToken(finalA.getId(), true))
                .isInstanceOf(MailboxException.class)
                .satisfies(e -> assertThat(((MailboxException) e).kind()).isEqualTo(MailboxException.Kind.AUTH_REVOKED));
    }

    @Test
    void restClientMapsGmailResponsesAndErrors() {
        GOOGLE.respond("/gmail/v1/users/me/messages?maxResults=100", 200,
                "{\"messages\":[{\"id\":\"a1\",\"threadId\":\"t1\"},{\"id\":\"a2\",\"threadId\":\"t2\"}],\"nextPageToken\":\"NEXT\"}");
        String raw = java.util.Base64.getUrlEncoder().encodeToString("From: x@y.com\r\n\r\nhello".getBytes());
        GOOGLE.respond("/gmail/v1/users/me/messages/a1", 200, "{\"id\":\"a1\",\"threadId\":\"t1\",\"labelIds\":[\"INBOX\"],"
                + "\"historyId\":\"900\",\"internalDate\":\"1762000000000\",\"snippet\":\"hello\",\"raw\":\"" + raw + "\"}");
        GOOGLE.respond("/gmail/v1/users/me/history", 404, "{\"error\":{\"code\":404,\"status\":\"NOT_FOUND\"}}");
        GOOGLE.respond("/gmail/v1/users/me/messages/slow", 429, "{\"error\":{\"errors\":[{\"reason\":\"rateLimitExceeded\"}]}}");
        GOOGLE.respond("/gmail/v1/users/me/messages/denied", 403, "{\"error\":{\"errors\":[{\"reason\":\"insufficientPermissions\"}]}}");

        var client = new GmailRestMailboxClient(GOOGLE.baseUrl(), java.time.Duration.ofSeconds(5), force -> "tok");
        var page = client.listMessages("after:1", null);
        assertThat(page.messageIds()).containsExactly("a1", "a2");
        assertThat(page.nextPageToken()).isEqualTo("NEXT");
        var msg = client.getRawMessage("a1");
        assertThat(new String(msg.raw())).contains("hello");
        assertThat(msg.labelIds()).containsExactly("INBOX");
        assertThat(msg.internalDate()).isEqualTo(java.time.Instant.ofEpochMilli(1762000000000L));
        assertKind(() -> client.listHistory(java.math.BigInteger.ONE, null), MailboxException.Kind.HISTORY_EXPIRED);
        assertKind(() -> client.getRawMessage("slow"), MailboxException.Kind.TRANSIENT);
        assertKind(() -> client.getRawMessage("denied"), MailboxException.Kind.AUTH_REVOKED);
        assertKind(() -> client.getRawMessage("missing"), MailboxException.Kind.NOT_FOUND);
    }

    private static void assertKind(org.assertj.core.api.ThrowableAssert.ThrowingCallable c, MailboxException.Kind kind) {
        org.assertj.core.api.Assertions.assertThatThrownBy(c).isInstanceOf(MailboxException.class)
                .satisfies(e -> assertThat(((MailboxException) e).kind()).isEqualTo(kind));
    }

    @Test
    void disconnectRevokesAndDeletesTokens() throws Exception {
        GmailAccount a = new GmailAccount(owner.getWorkspaceId(), "c@example.com");
        a.connect(cipher.encrypt("1//refresh"), cipher.encrypt("at"), java.time.Instant.now().plusSeconds(600), "s", java.time.Instant.now());
        a = accounts.save(a);
        GOOGLE.respond("/revoke", 200, "{}");
        mvc.perform(post("/api/v1/gmail/accounts/" + a.getId() + "/disconnect").with(as(owner)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISCONNECTED"));
        GmailAccount after = accounts.findById(a.getId()).orElseThrow();
        assertThat(after.getRefreshTokenEncrypted()).isNull();
        assertThat(after.getAccessTokenEncrypted()).isNull();
        assertThat(GOOGLE.requests.getLast().path()).isEqualTo("/revoke");
    }
}
