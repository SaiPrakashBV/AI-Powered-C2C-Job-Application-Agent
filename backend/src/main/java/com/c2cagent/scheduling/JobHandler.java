package com.c2cagent.scheduling;

/**
 * Extension point: implement as a Spring bean to process one job type. Handlers must be idempotent because a
 * job can be re-run after a crash. Throw {@link JobFailure#transientFailure} for retryable problems (network,
 * rate limit) and {@link JobFailure#permanent} for problems a retry cannot fix.
 */
public interface JobHandler {
    String jobType();

    void handle(BackgroundJob job) throws Exception;
}
