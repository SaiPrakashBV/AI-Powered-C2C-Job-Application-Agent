package com.c2cagent.gmail;

import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Resolves the Google OAuth desktop client (id + secret) from properties or Google's client JSON file. */
@Component
public class GmailClientConfig {
    private static final Logger log = LoggerFactory.getLogger(GmailClientConfig.class);
    private final GmailProperties props;
    private final Environment env;

    public record Client(String clientId, String clientSecret, String source) {}

    public GmailClientConfig(GmailProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    public Optional<Client> client() {
        if (props.clientId() != null && !props.clientId().isBlank()) {
            return Optional.of(new Client(props.clientId().strip(), blankToNull(props.clientSecret()), "properties"));
        }
        if (props.clientConfigFile() != null && Files.isRegularFile(props.clientConfigFile())) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> json = JsonMapper.builder().build().readValue(
                        Files.readString(props.clientConfigFile()), Map.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> inst = (Map<String, Object>) (json.containsKey("installed") ? json.get("installed") : json.get("web"));
                if (inst != null && inst.get("client_id") != null) {
                    return Optional.of(new Client(inst.get("client_id").toString(),
                            inst.get("client_secret") == null ? null : inst.get("client_secret").toString(), "file"));
                }
                log.warn("Gmail client config file has no 'installed.client_id'");
            } catch (Exception e) {
                log.warn("Cannot read Gmail client config file: {}", e.getMessage());
            }
        }
        return Optional.empty();
    }

    public String redirectUri() {
        if (props.redirectUri() != null && !props.redirectUri().isBlank()) {
            return props.redirectUri();
        }
        return "http://127.0.0.1:" + env.getProperty("server.port", "8080") + "/api/v1/gmail/oauth/callback";
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.strip(); }
}
