package com.c2cagent.email;

/**
 * Outcome of sender/source filtering. ALLOWED and DISCOVERY messages continue to content classification — an
 * allowed sender never implies a job requirement. BLOCKED, UNLISTED and OWN_SENT are excluded from the pipeline.
 */
public enum SourceDecision {
    ALLOWED, BLOCKED, DISCOVERY, UNLISTED, OWN_SENT;

    public boolean continuesToClassification() { return this == ALLOWED || this == DISCOVERY; }
}
