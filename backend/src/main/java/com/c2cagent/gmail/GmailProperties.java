package com.c2cagent.gmail;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Install-time Gmail/Google OAuth configuration ({@code app.gmail.*}). The OAuth client is a Google Cloud
 * "Desktop app" client: either point {@code client-config-file} at the downloaded client JSON, or set
 * {@code client-id}/{@code client-secret}. Endpoints are configurable so tests can use local stubs.
 */
@ConfigurationProperties(prefix = "app.gmail")
public record GmailProperties(
        Path clientConfigFile,
        String clientId,
        String clientSecret,
        @DefaultValue("https://accounts.google.com/o/oauth2/v2/auth") String authUri,
        @DefaultValue("https://oauth2.googleapis.com/token") String tokenUri,
        @DefaultValue("https://oauth2.googleapis.com/revoke") String revokeUri,
        @DefaultValue("https://gmail.googleapis.com") String apiBaseUrl,
        // Read messages + create drafts/send (compose includes send). No modify/delete scope is requested.
        @DefaultValue("https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/gmail.compose")
        String scopes,
        String redirectUri,
        @DefaultValue("PT30S") Duration requestTimeout,
        @DefaultValue("2000") int maxMessagesPerSync,
        @DefaultValue("true") boolean schedulerEnabled) {}
