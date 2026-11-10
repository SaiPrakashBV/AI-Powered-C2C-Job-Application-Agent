package com.c2cagent.scheduling;

import com.c2cagent.common.ApiException;
import com.c2cagent.configuration.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable PostgreSQL-backed job queue. All state transitions are single SQL statements guarded by status. */
@Service
public class BackgroundJobService {
    private final BackgroundJobRepository repository;
    private final JdbcTemplate jdbc;
    private final AppProperties.Jobs config;
    private final Clock clock;

    public BackgroundJobService(BackgroundJobRepository repository, JdbcTemplate jdbc, AppProperties props,
                                Optional<Clock> clock) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.config = props.jobs();
        this.clock = clock.orElse(Clock.systemUTC());
    }

    /**
     * Enqueues a job. With a dedup key, at most one QUEUED/RUNNING job of that type and key exists; a second
     * enqueue returns the existing job instead of creating a duplicate (idempotent).
     */
    @Transactional
    public BackgroundJob enqueue(UUID workspaceId, String jobType, String payloadJson, String dedupKey,
                                 int maxAttempts) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO background_job (id, workspace_id, job_type, status, payload, dedup_key, max_attempts,
                                            run_after, created_at, updated_at, version)
                VALUES (?, ?, ?, 'QUEUED', ?::jsonb, ?, ?, ?, now(), now(), 0)
                ON CONFLICT (job_type, dedup_key) WHERE dedup_key IS NOT NULL AND status IN ('QUEUED','RUNNING')
                DO NOTHING
                """, id, workspaceId, jobType, payloadJson, dedupKey, maxAttempts, Timestamp.from(clock.instant()));
        if (inserted == 1) {
            return repository.findById(id).orElseThrow();
        }
        UUID existing = jdbc.queryForObject("""
                SELECT id FROM background_job WHERE job_type = ? AND dedup_key = ? AND status IN ('QUEUED','RUNNING')
                """, UUID.class, jobType, dedupKey);
        return repository.findById(existing).orElseThrow();
    }

    /** Atomically claims up to {@code limit} due jobs for this worker (safe with multiple workers). */
    @Transactional
    public List<UUID> claim(String workerId, int limit) {
        return jdbc.queryForList("""
                UPDATE background_job SET status = 'RUNNING', locked_by = ?, locked_at = ?, attempts = attempts + 1,
                       updated_at = now(), version = version + 1
                WHERE id IN (SELECT id FROM background_job WHERE status = 'QUEUED' AND run_after <= ?
                             ORDER BY run_after LIMIT ? FOR UPDATE SKIP LOCKED)
                RETURNING id
                """, UUID.class, workerId, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), limit);
    }

    @Transactional
    public void markSucceeded(UUID jobId, String workerId) {
        jdbc.update("""
                UPDATE background_job SET status = 'SUCCEEDED', completed_at = ?, locked_by = NULL, locked_at = NULL,
                       last_error = NULL, failure_kind = NULL, updated_at = now(), version = version + 1
                WHERE id = ? AND status = 'RUNNING' AND locked_by = ?
                """, Timestamp.from(clock.instant()), jobId, workerId);
    }

    /** Records a failure: transient → re-queued with bounded exponential backoff, or DEAD when exhausted. */
    @Transactional
    public JobStatus markFailed(UUID jobId, String workerId, String error, boolean permanent) {
        BackgroundJob job = repository.findById(jobId).orElseThrow();
        String safeError = error == null ? "Unknown error" : (error.length() > 2000 ? error.substring(0, 2000) : error);
        JobStatus next;
        Instant runAfter = clock.instant();
        if (permanent) {
            next = JobStatus.FAILED;
        } else if (job.getAttempts() >= job.getMaxAttempts()) {
            next = JobStatus.DEAD;
        } else {
            next = JobStatus.QUEUED;
            runAfter = runAfter.plus(backoff(job.getAttempts()));
        }
        jdbc.update("""
                UPDATE background_job SET status = ?, run_after = ?, last_error = ?, failure_kind = ?,
                       locked_by = NULL, locked_at = NULL, completed_at = ?, updated_at = now(), version = version + 1
                WHERE id = ? AND status = 'RUNNING' AND locked_by = ?
                """, next.name(), Timestamp.from(runAfter), safeError, permanent ? "PERMANENT" : "TRANSIENT",
                next == JobStatus.QUEUED ? null : Timestamp.from(clock.instant()), jobId, workerId);
        return next;
    }

    Duration backoff(int attempts) {
        long factor = 1L << Math.min(Math.max(attempts - 1, 0), 20);
        Duration d = config.baseBackoff().multipliedBy(factor);
        return d.compareTo(config.maxBackoff()) > 0 ? config.maxBackoff() : d;
    }

    /**
     * Startup/periodic recovery: RUNNING jobs owned by a previous process (or whose lock is stale) are returned
     * to the queue. The attempt already counted, so a job that repeatedly crashes the app eventually goes DEAD.
     */
    @Transactional
    public int recoverOrphaned(String currentWorkerId) {
        Instant staleBefore = clock.instant().minus(config.staleLockTimeout());
        return jdbc.update("""
                UPDATE background_job SET status = CASE WHEN attempts >= max_attempts THEN 'DEAD' ELSE 'QUEUED' END,
                       last_error = 'Recovered after interruption (application restart or stale lock)',
                       failure_kind = 'TRANSIENT', locked_by = NULL, locked_at = NULL, updated_at = now(),
                       version = version + 1
                WHERE status = 'RUNNING' AND (locked_by IS DISTINCT FROM ? OR locked_at < ?)
                """, currentWorkerId, Timestamp.from(staleBefore));
    }

    /** Manual reprocessing of a FAILED/DEAD/CANCELLED job from the failed-job review screen. */
    @Transactional
    public BackgroundJob retry(UUID workspaceId, UUID jobId) {
        int n = jdbc.update("""
                UPDATE background_job SET status = 'QUEUED', attempts = 0, run_after = now(), completed_at = NULL,
                       updated_at = now(), version = version + 1
                WHERE id = ? AND workspace_id = ? AND status IN ('FAILED','DEAD','CANCELLED')
                """, jobId, workspaceId);
        if (n == 0) {
            throw ApiException.conflict("JOB_NOT_RETRYABLE", "Only failed, dead or cancelled jobs can be retried");
        }
        return repository.findById(jobId).orElseThrow();
    }

    @Transactional
    public BackgroundJob cancel(UUID workspaceId, UUID jobId) {
        int n = jdbc.update("""
                UPDATE background_job SET status = 'CANCELLED', completed_at = now(), updated_at = now(),
                       version = version + 1
                WHERE id = ? AND workspace_id = ? AND status = 'QUEUED'
                """, jobId, workspaceId);
        if (n == 0) {
            throw ApiException.conflict("JOB_NOT_CANCELLABLE", "Only queued jobs can be cancelled");
        }
        return repository.findById(jobId).orElseThrow();
    }
}
