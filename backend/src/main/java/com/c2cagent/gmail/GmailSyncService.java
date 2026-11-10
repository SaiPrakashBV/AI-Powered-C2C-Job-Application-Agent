package com.c2cagent.gmail;

import com.c2cagent.audit.AuditService;
import com.c2cagent.email.EmailIngestionService;
import com.c2cagent.scheduling.JobFailure;
import com.c2cagent.users.SettingsService;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Synchronizes one mailbox. First run: records the current history id, then backfills messages newer than the
 * configured number of days. Later runs: incremental history.list from the checkpoint. If the checkpoint expired,
 * a bounded re-scan since the last successful sync is done. Message storage is idempotent, so duplicate history
 * events, retries and restarts never create duplicate emails.
 */
@Service
public class GmailSyncService {
    private static final Logger log = LoggerFactory.getLogger(GmailSyncService.class);
    private final GmailAccountRepository accounts;
    private final GmailSyncCheckpointRepository checkpoints;
    private final MailboxClientFactory clients;
    private final EmailIngestionService ingestion;
    private final SettingsService settings;
    private final AuditService audit;
    private final GmailProperties props;
    private final Clock clock;

    public record SyncResult(String mode, int newMessages, int skippedExisting, boolean truncated) {}

    public GmailSyncService(GmailAccountRepository accounts, GmailSyncCheckpointRepository checkpoints,
                            MailboxClientFactory clients, EmailIngestionService ingestion, SettingsService settings,
                            AuditService audit, GmailProperties props, Optional<Clock> clock) {
        this.accounts = accounts;
        this.checkpoints = checkpoints;
        this.clients = clients;
        this.ingestion = ingestion;
        this.settings = settings;
        this.audit = audit;
        this.props = props;
        this.clock = clock.orElse(Clock.systemUTC());
    }

    public SyncResult sync(UUID accountId) {
        GmailAccount account = accounts.findById(accountId).orElseThrow(() -> JobFailure.permanent("Gmail account not found"));
        if (account.getStatus() != GmailAccountStatus.CONNECTED || !account.isSyncEnabled()) {
            return new SyncResult("SKIPPED", 0, 0, false);
        }
        GmailSyncCheckpoint cp = checkpoints.findById(accountId).orElseGet(() -> new GmailSyncCheckpoint(accountId));
        MailboxClient client = clients.forAccount(account);
        try {
            SyncResult result;
            if (cp.getHistoryId() == null) {
                int days = settings.getOrCreate(account.getWorkspaceId()).getGmailBackfillDays();
                result = backfill(account, client, cp, clock.instant().minus(Duration.ofDays(days)), "BACKFILL");
            } else {
                try {
                    result = incremental(account, client, cp);
                } catch (MailboxException e) {
                    if (e.kind() != MailboxException.Kind.HISTORY_EXPIRED) throw e;
                    Instant since = cp.getLastIncrementalAt() != null ? cp.getLastIncrementalAt().minus(Duration.ofDays(1))
                            : clock.instant().minus(Duration.ofDays(settings.getOrCreate(account.getWorkspaceId()).getGmailBackfillDays()));
                    audit.recordSystem(account.getWorkspaceId(), "GMAIL_HISTORY_EXPIRED", "gmail_account",
                            accountId.toString(), "Gmail sync checkpoint expired; re-scanning since " + since, Map.of());
                    result = backfill(account, client, cp, since, "RESCAN");
                }
            }
            reloadAndRecord(accountId, result.truncated() ? "PARTIAL" : "OK",
                    result.truncated() ? "Sync stopped after " + props.maxMessagesPerSync()
                            + " messages; the rest will be fetched on the next run" : null);
            if (result.newMessages() > 0) {
                log.info("Gmail sync {} for account {}: {} new message(s)", result.mode(), accountId, result.newMessages());
            }
            return result;
        } catch (MailboxException e) {
            if (e.kind() == MailboxException.Kind.AUTH_REVOKED) {
                GmailAccount a = accounts.findById(accountId).orElseThrow();
                a.requireReauth(e.getMessage());
                accounts.save(a);
                audit.recordSystem(a.getWorkspaceId(), "GMAIL_REAUTH_REQUIRED", "gmail_account", accountId.toString(),
                        "Gmail access for " + a.getEmailAddress() + " was revoked or expired; reconnect required", Map.of());
                throw JobFailure.permanent(e.getMessage());
            }
            reloadAndRecord(accountId, "ERROR", e.getMessage());
            if (e.kind() == MailboxException.Kind.PERMANENT) throw JobFailure.permanent(e.getMessage());
            throw JobFailure.transientFailure(e.getMessage(), e);
        }
    }

    private SyncResult backfill(GmailAccount account, MailboxClient client, GmailSyncCheckpoint cp, Instant since,
                                String mode) {
        // Record the history position BEFORE listing so nothing that arrives during the backfill is missed.
        BigInteger startHistory = client.profile().historyId();
        String query = "after:" + since.getEpochSecond();
        int added = 0, skipped = 0, seen = 0;
        boolean truncated = false;
        String page = null;
        do {
            MailboxClient.IdPage ids = client.listMessages(query, page);
            for (String id : ids.messageIds()) {
                if (++seen > props.maxMessagesPerSync()) { truncated = true; break; }
                if (fetchAndStore(account, client, id)) added++; else skipped++;
            }
            page = ids.nextPageToken();
        } while (page != null && !truncated);
        if (!truncated) {
            cp.setHistoryId(startHistory);
            cp.setBackfillFrom(since);
            cp.setBackfillCompletedAt(clock.instant());
            cp.setLastIncrementalAt(clock.instant());
        }
        cp.addMessagesSynced(added);
        checkpoints.save(cp);
        return new SyncResult(mode, added, skipped, truncated);
    }

    private SyncResult incremental(GmailAccount account, MailboxClient client, GmailSyncCheckpoint cp) {
        Set<String> ids = new LinkedHashSet<>();
        BigInteger latest = cp.getHistoryId();
        String page = null;
        do {
            MailboxClient.HistoryPage h = client.listHistory(cp.getHistoryId(), page);
            ids.addAll(h.addedMessageIds());   // duplicates across history records collapse here
            if (h.historyId() != null && h.historyId().compareTo(latest) > 0) latest = h.historyId();
            page = h.nextPageToken();
        } while (page != null);
        int added = 0, skipped = 0, seen = 0;
        boolean truncated = false;
        for (String id : ids) {
            if (++seen > props.maxMessagesPerSync()) { truncated = true; break; }
            if (fetchAndStore(account, client, id)) added++; else skipped++;
        }
        if (!truncated) {
            cp.setHistoryId(latest);
            cp.setLastIncrementalAt(clock.instant());
        }
        cp.addMessagesSynced(added);
        checkpoints.save(cp);
        return new SyncResult("INCREMENTAL", added, skipped, truncated);
    }

    private boolean fetchAndStore(GmailAccount account, MailboxClient client, String id) {
        if (ingestion.alreadyStored(account, id)) return false;
        try {
            return ingestion.ingest(account, client.getRawMessage(id));
        } catch (MailboxException e) {
            if (e.kind() == MailboxException.Kind.NOT_FOUND) return false; // deleted in the meantime
            throw e;
        }
    }

    private void reloadAndRecord(UUID accountId, String status, String error) {
        GmailAccount a = accounts.findById(accountId).orElseThrow();
        a.recordSync(status, error, clock.instant());
        accounts.save(a);
    }
}
