package com.c2cagent.gmail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Persistent sync position per mailbox, so sync resumes after restarts/shutdowns. */
@Entity
@Table(name = "gmail_sync_checkpoint")
public class GmailSyncCheckpoint {
    @Id
    @Column(name = "gmail_account_id") private UUID gmailAccountId;
    @Column(name = "history_id", precision = 30) private BigInteger historyId;
    @Column(name = "backfill_completed_at") private Instant backfillCompletedAt;
    @Column(name = "backfill_from") private Instant backfillFrom;
    @Column(name = "last_incremental_at") private Instant lastIncrementalAt;
    @Column(name = "messages_synced", nullable = false) private long messagesSynced;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected GmailSyncCheckpoint() {}

    public GmailSyncCheckpoint(UUID gmailAccountId) { this.gmailAccountId = gmailAccountId; }

    public UUID getGmailAccountId() { return gmailAccountId; }
    public BigInteger getHistoryId() { return historyId; }
    public void setHistoryId(BigInteger h) { historyId = h; }
    public Instant getBackfillCompletedAt() { return backfillCompletedAt; }
    public void setBackfillCompletedAt(Instant t) { backfillCompletedAt = t; }
    public Instant getBackfillFrom() { return backfillFrom; }
    public void setBackfillFrom(Instant t) { backfillFrom = t; }
    public Instant getLastIncrementalAt() { return lastIncrementalAt; }
    public void setLastIncrementalAt(Instant t) { lastIncrementalAt = t; }
    public long getMessagesSynced() { return messagesSynced; }
    public void addMessagesSynced(long n) { messagesSynced += n; }
    public void reset() { historyId = null; backfillCompletedAt = null; }
}
