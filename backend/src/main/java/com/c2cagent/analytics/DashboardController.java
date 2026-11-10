package com.c2cagent.analytics;

import com.c2cagent.audit.AuditController;
import com.c2cagent.audit.AuditEventRepository;
import com.c2cagent.auth.CurrentUser;
import com.c2cagent.configuration.AppProperties;
import com.c2cagent.scheduling.BackgroundJobRepository;
import com.c2cagent.scheduling.JobStatus;
import com.c2cagent.users.AutomationMode;
import com.c2cagent.users.SettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard summary. Only reports metrics backed by real data; pipeline metrics arrive with their phases. */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {
    private final SettingsService settings;
    private final BackgroundJobRepository jobs;
    private final AuditEventRepository auditEvents;
    private final JdbcTemplate jdbc;
    private final AppProperties props;

    public DashboardController(SettingsService settings, BackgroundJobRepository jobs,
                               AuditEventRepository auditEvents, JdbcTemplate jdbc, AppProperties props) {
        this.settings = settings;
        this.jobs = jobs;
        this.auditEvents = auditEvents;
        this.jdbc = jdbc;
        this.props = props;
    }

    public record Safety(AutomationMode automationMode, boolean emergencyStop, boolean outboundDryRun,
                         boolean externalAiConsent) {}

    public record SystemStatus(String database, String schemaVersion, boolean demoMode) {}

    public record DashboardSummary(String displayName, Safety safety, SystemStatus system,
                                   Map<JobStatus, Long> jobCounts, List<AuditController.AuditEventDto> recentActivity,
                                   List<ModuleCatalog.ModuleInfo> modules) {}

    @GetMapping("/summary")
    public DashboardSummary summary(CurrentUser user) {
        var s = settings.getOrCreate(user.workspaceId());
        Map<JobStatus, Long> counts = new EnumMap<>(JobStatus.class);
        for (JobStatus st : JobStatus.values()) {
            counts.put(st, 0L);
        }
        jobs.countByStatus(user.workspaceId()).forEach(c -> counts.put(c.getStatus(), c.getCount()));
        String db;
        String schema = null;
        try {
            schema = jdbc.queryForObject(
                    "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                    String.class);
            db = "UP";
        } catch (Exception e) {
            db = "DOWN";
        }
        var recent = auditEvents.findTop10ByWorkspaceIdOrderByCreatedAtDesc(user.workspaceId()).stream()
                .map(e -> new AuditController.AuditEventDto(e.getId(), e.getActorType(), e.getEventType(),
                        e.getEntityType(), e.getEntityId(), e.getSummary(), null, e.getCreatedAt()))
                .toList();
        return new DashboardSummary(user.displayName(),
                new Safety(s.getAutomationMode(), s.isEmergencyStop(), s.isOutboundDryRun(), s.isExternalAiConsent()),
                new SystemStatus(db, schema, props.demoMode()), counts, recent, ModuleCatalog.MODULES);
    }
}
