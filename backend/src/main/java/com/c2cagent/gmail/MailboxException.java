package com.c2cagent.gmail;

/** Classified mailbox/OAuth failure so callers can decide between retry, re-auth and skip. */
public class MailboxException extends RuntimeException {
    public enum Kind {
        /** Network error, timeout, 5xx, rate limit, expired access token: retry later with backoff. */
        TRANSIENT,
        /** Refresh token revoked/expired or permission removed: the user must reconnect. */
        AUTH_REVOKED,
        /** Stored history id is too old (Gmail 404 on history.list): a bounded re-scan is needed. */
        HISTORY_EXPIRED,
        /** The message no longer exists. */
        NOT_FOUND,
        /** Anything a retry cannot fix (bad request, misconfiguration). */
        PERMANENT
    }

    private final Kind kind;

    public MailboxException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() { return kind; }
}
