package com.c2cagent.configuration;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application-level (install-time) configuration bound from {@code app.*}.
 * User-editable business preferences live in the database (workspace_settings etc.), not here.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("${user.home}/C2CAgent/data") Path dataDir,
        @DefaultValue("false") boolean demoMode,
        @DefaultValue Security security,
        @DefaultValue Jobs jobs) {

    public record Security(
            @DefaultValue("5") int maxFailedLogins,
            @DefaultValue("PT15M") Duration lockoutDuration,
            @DefaultValue("12") int minPasswordLength) {}

    public record Jobs(
            @DefaultValue("true") boolean workerEnabled,
            @DefaultValue("5") int batchSize,
            @DefaultValue("PT10S") Duration baseBackoff,
            @DefaultValue("PT30M") Duration maxBackoff,
            @DefaultValue("PT30M") Duration staleLockTimeout) {}
}
