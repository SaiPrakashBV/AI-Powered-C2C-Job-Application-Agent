package com.c2cagent.gmail;

import java.math.BigInteger;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Gmail REST API v1 client (users.getProfile, messages.list, history.list, messages.get?format=raw).
 * HTTP status codes are mapped to {@link MailboxException.Kind}: 401 → refresh token once then TRANSIENT,
 * 403 rate-limit/429/5xx → TRANSIENT, other 403 → AUTH_REVOKED (permission missing), 404 → HISTORY_EXPIRED or
 * NOT_FOUND.
 */
public class GmailRestMailboxClient implements MailboxClient {
    private final String base;
    private final HttpClient http;
    private final Duration timeout;
    private final Function<Boolean, String> tokenSupplier; // arg: force refresh

    public GmailRestMailboxClient(String apiBaseUrl, Duration timeout, Function<Boolean, String> tokenSupplier) {
        this.base = apiBaseUrl.replaceAll("/+$", "") + "/gmail/v1/users/me";
        this.http = OAuthHttp.client(timeout);
        this.timeout = timeout;
        this.tokenSupplier = tokenSupplier;
    }

    @Override
    public Profile profile() {
        Map<String, Object> b = call(base + "/profile", false);
        return new Profile(str(b.get("emailAddress")), bigInt(b.get("historyId")));
    }

    @Override
    @SuppressWarnings("unchecked")
    public IdPage listMessages(String query, String pageToken) {
        StringBuilder url = new StringBuilder(base).append("/messages?maxResults=100&q=").append(OAuthHttp.enc(query));
        if (pageToken != null) url.append("&pageToken=").append(OAuthHttp.enc(pageToken));
        Map<String, Object> b = call(url.toString(), false);
        List<String> ids = new ArrayList<>();
        Object msgs = b.get("messages");
        if (msgs instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m && m.get("id") != null) ids.add(m.get("id").toString());
            }
        }
        return new IdPage(ids, str(b.get("nextPageToken")));
    }

    @Override
    public HistoryPage listHistory(BigInteger startHistoryId, String pageToken) {
        StringBuilder url = new StringBuilder(base).append("/history?historyTypes=messageAdded&maxResults=500&startHistoryId=")
                .append(startHistoryId);
        if (pageToken != null) url.append("&pageToken=").append(OAuthHttp.enc(pageToken));
        Map<String, Object> b = call(url.toString(), true);
        List<String> ids = new ArrayList<>();
        if (b.get("history") instanceof List<?> hist) {
            for (Object h : hist) {
                if (h instanceof Map<?, ?> hm && hm.get("messagesAdded") instanceof List<?> added) {
                    for (Object a : added) {
                        if (a instanceof Map<?, ?> am && am.get("message") instanceof Map<?, ?> msg && msg.get("id") != null) {
                            ids.add(msg.get("id").toString());
                        }
                    }
                }
            }
        }
        return new HistoryPage(ids, str(b.get("nextPageToken")), bigInt(b.get("historyId")));
    }

    @Override
    public RawMessage getRawMessage(String messageId) {
        Map<String, Object> b = call(base + "/messages/" + OAuthHttp.enc(messageId) + "?format=raw", false);
        List<String> labels = new ArrayList<>();
        if (b.get("labelIds") instanceof List<?> l) {
            l.forEach(x -> labels.add(x.toString()));
        }
        Object internal = b.get("internalDate");
        Instant date = internal == null ? null : Instant.ofEpochMilli(Long.parseLong(internal.toString()));
        String raw = str(b.get("raw"));
        if (raw == null) {
            throw new MailboxException(MailboxException.Kind.PERMANENT, "Gmail returned no raw content for message");
        }
        return new RawMessage(str(b.get("id")), str(b.get("threadId")), labels, bigInt(b.get("historyId")), date,
                str(b.get("snippet")), Base64.getUrlDecoder().decode(raw));
    }

    private Map<String, Object> call(String url, boolean historyCall) {
        OAuthHttp.Response r = OAuthHttp.get(http, url, tokenSupplier.apply(false), timeout);
        if (r.status() == 401) {
            r = OAuthHttp.get(http, url, tokenSupplier.apply(true), timeout); // token expired early: refresh once
        }
        int s = r.status();
        if (s == 200) return r.body();
        String reason = OAuthHttp.errorReason(r);
        if (s == 404) {
            throw new MailboxException(historyCall ? MailboxException.Kind.HISTORY_EXPIRED : MailboxException.Kind.NOT_FOUND,
                    historyCall ? "Gmail history checkpoint expired" : "Message not found");
        }
        if (s == 429 || s >= 500 || (s == 403 && reason.toLowerCase().contains("ratelimit"))) {
            throw new MailboxException(MailboxException.Kind.TRANSIENT, "Gmail temporarily unavailable (" + reason + ")");
        }
        if (s == 401 || s == 403) {
            throw new MailboxException(MailboxException.Kind.AUTH_REVOKED,
                    "Gmail denied access (" + reason + "); reconnect the account and grant the requested permissions");
        }
        throw new MailboxException(MailboxException.Kind.PERMANENT, "Gmail request failed (HTTP " + s + ", " + reason + ")");
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static BigInteger bigInt(Object o) { return o == null ? null : new BigInteger(o.toString()); }
}
