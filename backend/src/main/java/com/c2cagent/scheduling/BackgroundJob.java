package com.c2cagent.scheduling;

import com.c2cagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "background_job")
public class BackgroundJob extends BaseEntity {
    @Column(name = "workspace_id")
    private UUID workspaceId;
    @Column(name = "job_type", nullable = false, length = 80)
    private String jobType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.QUEUED;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;
    @Column(name = "dedup_key", length = 200)
    private String dedupKey;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;
    @Column(name = "run_after", nullable = false)
    private Instant runAfter;
    @Column(name = "locked_by", length = 100)
    private String lockedBy;
    @Column(name = "locked_at")
    private Instant lockedAt;
    @Column(name = "last_error")
    private String lastError;
    @Column(name = "failure_kind", length = 20)
    private String failureKind;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected BackgroundJob() {}

    public BackgroundJob(UUID workspaceId, String jobType, String payload, String dedupKey, int maxAttempts,
                         Instant runAfter) {
        this.workspaceId = workspaceId;
        this.jobType = jobType;
        this.payload = payload;
        this.dedupKey = dedupKey;
        this.maxAttempts = maxAttempts;
        this.runAfter = runAfter;
    }

    public UUID getWorkspaceId() { return workspaceId; }
    public String getJobType() { return jobType; }
    public JobStatus getStatus() { return status; }
    public String getPayload() { return payload; }
    public String getDedupKey() { return dedupKey; }
    public int getAttempts() { return attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getRunAfter() { return runAfter; }
    public String getLockedBy() { return lockedBy; }
    public Instant getLockedAt() { return lockedAt; }
    public String getLastError() { return lastError; }
    public String getFailureKind() { return failureKind; }
    public Instant getCompletedAt() { return completedAt; }
}
