package com.c2cagent.fixtures;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Local stand-in for Google's OAuth token endpoint and the Gmail REST API (no real Google calls in tests). */
public class StubGoogleServer {
    public record Recorded(String method, String path, String query, String body, String authorization) {}

    public final HttpServer server;
    public final List<Recorded> requests = new CopyOnWriteArrayList<>();
    /** path (+ optional "?"+query prefix) → [status, json body] */
    public final Map<String, Object[]> responses = new ConcurrentHashMap<>();

    public StubGoogleServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    public void respond(String pathAndQueryPrefix, int status, String json) {
        responses.put(pathAndQueryPrefix, new Object[] {status, json});
    }

    private void handle(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getRawQuery();
        requests.add(new Recorded(ex.getRequestMethod(), path, query, body, ex.getRequestHeaders().getFirst("Authorization")));
        String full = query == null ? path : path + "?" + query;
        Object[] r = responses.entrySet().stream().filter(e -> full.startsWith(e.getKey()))
                .max(Map.Entry.comparingByKey((a, b) -> Integer.compare(a.length(), b.length())))
                .map(Map.Entry::getValue).orElse(new Object[] {404, "{\"error\":{\"status\":\"NOT_FOUND\"}}"});
        byte[] out = ((String) r[1]).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders((Integer) r[0], out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }
}
