package com.c2cagent.scheduling;

/**
 * QUEUED → RUNNING → SUCCEEDED. Transient failure → QUEUED again (with backoff) until max attempts, then DEAD.
 * Permanent failure → FAILED. DEAD and FAILED jobs appear in the failed-job review and can be retried manually.
 */
public enum JobStatus { QUEUED, RUNNING, SUCCEEDED, FAILED, DEAD, CANCELLED }
