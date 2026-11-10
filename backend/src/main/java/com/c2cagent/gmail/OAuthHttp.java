package com.c2cagent.gmail;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.json.JsonMapper;

/** Minimal JSON-over-HTTP helper for Google OAuth and Gmail REST calls (java.net.http, no extra SDK). */
final class OAuthHttp {
    static final JsonMapper JSON = JsonMapper.builder().build();

    record Response(int status, Map<String, Object> body, String rawBody) {}

    private OAuthHttp() {}

    static HttpClient client(Duration timeout) {
        return HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    static Response postForm(HttpClient http, String url, Map<String, String> form, Duration timeout) {
        String body = form.entrySet().stream().filter(e -> e.getValue() != null)
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue())).collect(Collectors.joining("&"));
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return send(http, req);
    }

    static Response get(HttpClient http, String url, String accessToken, Duration timeout) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json").GET().build();
        return send(http, req);
    }

    @SuppressWarnings("unchecked")
    private static Response send(HttpClient http, HttpRequest req) {
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Map<String, Object> map = null;
            String raw = res.body();
            if (raw != null && raw.strip().startsWith("{")) {
                try {
                    map = JSON.readValue(raw, Map.class);
                } catch (RuntimeException ignored) {
                    // non-JSON error page
                }
            }
            return new Response(res.statusCode(), map == null ? Map.of() : map, raw);
        } catch (IOException e) {
            throw new MailboxException(MailboxException.Kind.TRANSIENT, "Network error contacting Google: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MailboxException(MailboxException.Kind.TRANSIENT, "Interrupted");
        }
    }

    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    /** Extracts Google's error reason without echoing tokens or bodies into logs. */
    @SuppressWarnings("unchecked")
    static String errorReason(Response r) {
        Object err = r.body().get("error");
        if (err instanceof String s) {
            return s;
        }
        if (err instanceof Map<?, ?> m) {
            Object errors = m.get("errors");
            if (errors instanceof java.util.List<?> l && !l.isEmpty() && l.getFirst() instanceof Map<?, ?> first
                    && first.get("reason") != null) {
                return first.get("reason").toString();
            }
            Object status = m.get("status");
            return status == null ? "http_" + r.status() : status.toString();
        }
        return "http_" + r.status();
    }
}
