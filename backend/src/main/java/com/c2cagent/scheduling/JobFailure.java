package com.c2cagent.scheduling;

public class JobFailure extends RuntimeException {
    private final boolean permanent;

    private JobFailure(String message, Throwable cause, boolean permanent) {
        super(message, cause);
        this.permanent = permanent;
    }

    public static JobFailure transientFailure(String message, Throwable cause) {
        return new JobFailure(message, cause, false);
    }

    public static JobFailure permanent(String message) {
        return new JobFailure(message, null, true);
    }

    public boolean isPermanent() { return permanent; }
}
