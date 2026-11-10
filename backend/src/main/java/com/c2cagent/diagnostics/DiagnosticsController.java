package com.c2cagent.diagnostics;

import com.c2cagent.auth.CurrentUser;
import com.c2cagent.configuration.AppProperties;
import com.c2cagent.configuration.DataDirectoryInitializer;
import com.c2cagent.scheduling.BackgroundJobController;
import com.c2cagent.scheduling.BackgroundJobService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.file.Files;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diagnostics")
@Tag(name = "Diagnostics")
public class DiagnosticsController {
    private final JdbcTemplate jdbc;
    private final DataDirectoryInitializer dataDir;
    private final AppProperties props;
    private final BackgroundJobService jobs;

    public DiagnosticsController(JdbcTemplate jdbc, DataDirectoryInitializer dataDir, AppProperties props,
                                 BackgroundJobService jobs) {
        this.jdbc = jdbc;
        this.dataDir = dataDir;
        this.props = props;
        this.jobs = jobs;
    }

    public record DiagnosticsResponse(String javaVersion, String os, String databaseVersion, String schemaVersion,
                                      String dataDirectory, boolean dataDirectoryWritable, boolean demoMode,
                                      boolean jobWorkerEnabled, long maxMemoryMb) {}

    @GetMapping
    public DiagnosticsResponse get(CurrentUser user) {
        String dbVersion = jdbc.queryForObject("SHOW server_version", String.class);
        String schema = jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                String.class);
        return new DiagnosticsResponse(System.getProperty("java.version"),
                System.getProperty("os.name") + " " + System.getProperty("os.version"), dbVersion, schema,
                dataDir.dataDir().toString(), Files.isWritable(dataDir.dataDir()), props.demoMode(),
                props.jobs().workerEnabled(), Runtime.getRuntime().maxMemory() / (1024 * 1024));
    }

    @PostMapping("/self-test")
    public BackgroundJobController.JobDto selfTest(CurrentUser user) {
        var job = jobs.enqueue(user.workspaceId(), SelfTestJobHandler.TYPE, null,
                "selftest:" + user.workspaceId(), 3);
        return new BackgroundJobController.JobDto(job.getId(), job.getJobType(), job.getStatus(), job.getAttempts(),
                job.getMaxAttempts(), job.getRunAfter(), job.getLastError(), job.getFailureKind(), job.getCreatedAt(),
                job.getCompletedAt());
    }
}
