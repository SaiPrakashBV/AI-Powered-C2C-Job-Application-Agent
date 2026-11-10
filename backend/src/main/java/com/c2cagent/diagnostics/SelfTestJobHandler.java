package com.c2cagent.diagnostics;

import com.c2cagent.audit.AuditService;
import com.c2cagent.configuration.DataDirectoryInitializer;
import com.c2cagent.scheduling.BackgroundJob;
import com.c2cagent.scheduling.JobFailure;
import com.c2cagent.scheduling.JobHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Real diagnostic job: verifies the database and data directory through the durable queue end to end. */
@Component
public class SelfTestJobHandler implements JobHandler {
    public static final String TYPE = "DIAGNOSTICS_SELF_TEST";
    private final JdbcTemplate jdbc;
    private final DataDirectoryInitializer dataDir;
    private final AuditService audit;

    public SelfTestJobHandler(JdbcTemplate jdbc, DataDirectoryInitializer dataDir, AuditService audit) {
        this.jdbc = jdbc;
        this.dataDir = dataDir;
        this.audit = audit;
    }

    @Override
    public String jobType() { return TYPE; }

    @Override
    public void handle(BackgroundJob job) throws Exception {
        Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
        if (one == null || one != 1) {
            throw JobFailure.transientFailure("Database check returned unexpected result", null);
        }
        Path probe = dataDir.dataDir().resolve("logs").resolve(".selftest-" + job.getId());
        try {
            Files.writeString(probe, "ok");
            Files.delete(probe);
        } catch (Exception e) {
            throw JobFailure.permanent("Data directory is not writable: " + dataDir.dataDir());
        }
        audit.recordSystem(job.getWorkspaceId(), "DIAGNOSTICS_SELF_TEST_PASSED", "background_job",
                job.getId().toString(), "Diagnostics self-test passed (database + data directory)", Map.of());
    }
}
