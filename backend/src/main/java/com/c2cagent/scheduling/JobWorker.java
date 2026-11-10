package com.c2cagent.scheduling;

import com.c2cagent.configuration.AppProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the durable queue and dispatches jobs to their {@link JobHandler}. */
@Component
public class JobWorker {
    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final BackgroundJobService jobs;
    private final BackgroundJobRepository repository;
    private final Map<String, JobHandler> handlers;
    private final AppProperties.Jobs config;
    private final String workerId = "worker-" + UUID.randomUUID();
    private volatile boolean shuttingDown;

    public JobWorker(BackgroundJobService jobs, BackgroundJobRepository repository, List<JobHandler> handlers,
                     AppProperties props) {
        this.jobs = jobs;
        this.repository = repository;
        this.handlers = handlers.stream().collect(Collectors.toMap(JobHandler::jobType, Function.identity()));
        this.config = props.jobs();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        int recovered = jobs.recoverOrphaned(workerId);
        if (recovered > 0) {
            log.warn("Recovered {} interrupted background job(s) after restart", recovered);
        }
    }

    @Scheduled(fixedDelayString = "${app.jobs.poll-interval:PT5S}", initialDelayString = "PT5S")
    public void poll() {
        if (config.workerEnabled() && !shuttingDown) {
            runOnce();
        }
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    public void sweepStale() {
        if (config.workerEnabled()) {
            jobs.recoverOrphaned(workerId);
        }
    }

    /** Claims and processes one batch. Returns the number of jobs processed. Public for deterministic tests. */
    public int runOnce() {
        List<UUID> claimed = jobs.claim(workerId, config.batchSize());
        for (UUID id : claimed) {
            BackgroundJob job = repository.findById(id).orElseThrow();
            JobHandler handler = handlers.get(job.getJobType());
            if (handler == null) {
                jobs.markFailed(id, workerId, "No handler registered for job type " + job.getJobType(), true);
                continue;
            }
            try {
                handler.handle(job);
                jobs.markSucceeded(id, workerId);
            } catch (JobFailure f) {
                JobStatus next = jobs.markFailed(id, workerId, f.getMessage(), f.isPermanent());
                log.warn("Job {} ({}) failed: {} -> {}", id, job.getJobType(), f.getMessage(), next);
            } catch (Exception e) {
                // Unknown exceptions are treated as transient so they are retried with backoff.
                JobStatus next = jobs.markFailed(id, workerId, e.getClass().getSimpleName() + ": " + e.getMessage(),
                        false);
                log.warn("Job {} ({}) threw {} -> {}", id, job.getJobType(), e.getClass().getSimpleName(), next);
            }
        }
        return claimed.size();
    }

    public void shutdown() { shuttingDown = true; }

    public String workerId() { return workerId; }
}
