package com.c2cagent.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c2cagent.IntegrationTestBase;
import com.c2cagent.configuration.SecretCipher;
import com.c2cagent.email.EmailAttachmentRepository;
import com.c2cagent.email.EmailSourceRule;
import com.c2cagent.email.EmailSourceRuleRepository;
import com.c2cagent.email.IncomingEmail;
import com.c2cagent.email.IncomingEmailRepository;
import com.c2cagent.email.ProcessingStatus;
import com.c2cagent.email.SourceDecision;
import com.c2cagent.email.SourceRuleType;
import com.c2cagent.fixtures.FakeMailbox;
import com.c2cagent.fixtures.TestEmails;
import com.c2cagent.scheduling.BackgroundJobRepository;
import com.c2cagent.scheduling.JobStatus;
import com.c2cagent.scheduling.JobWorker;
import com.c2cagent.users.AppUser;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(GmailSyncIT.FakeClients.class)
class GmailSyncIT extends IntegrationTestBase {
    static final Map<String, FakeMailbox> MAILBOXES = new ConcurrentHashMap<>();

    @TestConfiguration
    static class FakeClients {
        @Bean
        @Primary
        MailboxClientFactory fakeFactory() {
            return account -> MAILBOXES.get(account.getEmailAddress());
        }
    }

    @Autowired GmailAccountRepository accounts;
    @Autowired GmailSyncCheckpointRepository checkpoints;
    @Autowired GmailSyncService sync;
    @Autowired GmailSyncScheduler scheduler;
    @Autowired IncomingEmailRepository emails;
    @Autowired EmailAttachmentRepository attachments;
    @Autowired EmailSourceRuleRepository rules;
    @Autowired BackgroundJobRepository jobs;
    @Autowired JobWorker worker;
    @Autowired SecretCipher cipher;

    AppUser owner;
    GmailAccount account;
    FakeMailbox box;

    @BeforeEach
    void setUp() {
        MAILBOXES.clear();
        owner = createOwner("owner");
        account = new GmailAccount(owner.getWorkspaceId(), "candidate@example.com");
        account.connect(cipher.encrypt("refresh"), cipher.encrypt("access"), Instant.now().plusSeconds(3600), "gmail.readonly", Instant.now());
        account = accounts.save(account);
        box = new FakeMailbox("candidate@example.com");
        MAILBOXES.put("candidate@example.com", box);
        rules.save(new EmailSourceRule(owner.getWorkspaceId(), SourceRuleType.ALLOW_DOMAIN, "vendor-a.example", null, 100, true, null));
        rules.save(new EmailSourceRule(owner.getWorkspaceId(), SourceRuleType.BLOCK_ADDRESS, "blocked@vendor-a.example", null, 100, true, null));
    }

    private IncomingEmail email(String gmailId) {
        return emails.findAll().stream().filter(e -> e.getGmailMessageId().equals(gmailId)).findFirst().orElseThrow();
    }

    @Test
    void initialBackfillStoresMessagesAppliesRulesAndExcludesOwnSent() {
        byte[] pdf = TestEmails.pdf("Senior Java Developer - Spring Boot, AWS, Kafka");
        box.add("m1", TestEmails.message().text(TestEmails.JAVA_JD).bytes(), "INBOX")                 // no subject
           .add("m2", TestEmails.message().from("blocked@vendor-a.example").subject("JD").text(TestEmails.JAVA_JD).bytes(), "INBOX")
           .add("m3", TestEmails.message().from("Me <candidate@example.com>").subject("Re: Java role").text("My resume attached").bytes(), "SENT")
           .add("m4", TestEmails.message().from("someone@unknown.example").subject("Hi").text("hello").bytes(), "INBOX")
           .add("m5", TestEmails.message().subject("JD attached").text("see attachment")
                   .attach("JD.pdf", "application/pdf", pdf).bytes(), "INBOX");

        var result = sync.sync(account.getId());
        assertThat(result.mode()).isEqualTo("BACKFILL");
        assertThat(result.newMessages()).isEqualTo(5);

        IncomingEmail m1 = email("m1");
        assertThat(m1.getSubject()).isNull();
        assertThat(m1.getSourceDecision()).isEqualTo(SourceDecision.ALLOWED);
        assertThat(m1.getProcessingStatus()).isEqualTo(ProcessingStatus.PENDING_CLASSIFICATION);
        assertThat(m1.getTextBody()).contains("Senior Java Developer");

        IncomingEmail m2 = email("m2");   // scenario 15: blocked sender with a valid JD is not processed
        assertThat(m2.getSourceDecision()).isEqualTo(SourceDecision.BLOCKED);
        assertThat(m2.getProcessingStatus()).isEqualTo(ProcessingStatus.EXCLUDED);
        assertThat(m2.getTextBody()).as("bodies of excluded mail are not stored").isNull();

        IncomingEmail m3 = email("m3");
        assertThat(m3.isOwnSent()).isTrue();
        assertThat(m3.getSourceDecision()).isEqualTo(SourceDecision.OWN_SENT);
        assertThat(m3.getProcessingStatus()).isEqualTo(ProcessingStatus.EXCLUDED);

        assertThat(email("m4").getSourceDecision()).isEqualTo(SourceDecision.UNLISTED);

        var atts = attachments.findByIncomingEmailIdOrderByCreatedAtAsc(email("m5").getId());
        assertThat(atts).hasSize(1);
        assertThat(atts.getFirst().getExtractedText()).contains("Senior Java Developer");
        assertThat(atts.getFirst().getStoragePath()).isNotNull();

        var cp = checkpoints.findById(account.getId()).orElseThrow();
        assertThat(cp.getHistoryId()).isEqualTo(BigInteger.valueOf(1000));
        assertThat(cp.getBackfillCompletedAt()).isNotNull();
        assertThat(accounts.findById(account.getId()).orElseThrow().getLastSyncStatus()).isEqualTo("OK");
    }

    @Test
    void scenario60_incrementalSyncIsIdempotentForDuplicateHistoryEvents() {
        box.add("m1", TestEmails.message().text(TestEmails.JAVA_JD).bytes(), "INBOX");
        sync.sync(account.getId());
        int fetchesAfterBackfill = box.rawFetches;

        box.historyId = BigInteger.valueOf(1050);
        box.add("m2", TestEmails.message().subject("New req").text(TestEmails.JAVA_JD).bytes(), "INBOX");
        box.historyPages.add(List.of("m2", "m1"));
        box.historyPages.add(List.of("m2"));         // same message reported again on the next page

        var r = sync.sync(account.getId());
        assertThat(r.mode()).isEqualTo("INCREMENTAL");
        assertThat(r.newMessages()).isEqualTo(1);
        assertThat(box.rawFetches - fetchesAfterBackfill).as("already-stored messages are not refetched").isEqualTo(1);
        assertThat(emails.count()).isEqualTo(2);
        assertThat(checkpoints.findById(account.getId()).orElseThrow().getHistoryId()).isEqualTo(BigInteger.valueOf(1050));

        // Re-running (e.g. after a restart) changes nothing.
        assertThat(sync.sync(account.getId()).newMessages()).isZero();
        assertThat(emails.count()).isEqualTo(2);
    }

    @Test
    void expiredHistoryCheckpointFallsBackToBoundedRescan() {
        box.add("m1", TestEmails.message().text("a").bytes(), "INBOX");
        sync.sync(account.getId());
        box.historyExpired = true;
        box.add("m9", TestEmails.message().text("b").bytes(), "INBOX");
        var r = sync.sync(account.getId());
        assertThat(r.mode()).isEqualTo("RESCAN");
        assertThat(r.newMessages()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE event_type='GMAIL_HISTORY_EXPIRED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void scenario45_revokedAccessMarksAccountForReconnectAndJobFailsPermanently() throws Exception {
        box.failNext = new MailboxException(MailboxException.Kind.AUTH_REVOKED, "invalid_grant");
        mvc.perform(post("/api/v1/gmail/accounts/" + account.getId() + "/sync").with(as(owner)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jobType").value("GMAIL_SYNC"));
        worker.runOnce();
        assertThat(accounts.findById(account.getId()).orElseThrow().getStatus()).isEqualTo(GmailAccountStatus.REAUTH_REQUIRED);
        assertThat(jobs.findAll().getFirst().getStatus()).isEqualTo(JobStatus.FAILED);
        mvc.perform(post("/api/v1/gmail/accounts/" + account.getId() + "/sync").with(as(owner)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GMAIL_NOT_CONNECTED"));
    }

    @Test
    void transientErrorsAreRetriedAndDoNotLoseCheckpoint() {
        box.add("m1", TestEmails.message().text("a").bytes(), "INBOX");
        box.failNext = new MailboxException(MailboxException.Kind.TRANSIENT, "Gmail temporarily unavailable (rateLimitExceeded)");
        scheduler.enqueueDue();
        worker.runOnce();
        var job = jobs.findAll().getFirst();
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getLastError()).contains("rateLimitExceeded");
        assertThat(accounts.findById(account.getId()).orElseThrow().getStatus()).isEqualTo(GmailAccountStatus.CONNECTED);
        jdbc.update("UPDATE background_job SET run_after = now()");
        worker.runOnce();
        assertThat(jobs.findAll().getFirst().getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(emails.count()).isEqualTo(1);
    }

    @Test
    void schedulerOnlyEnqueuesDueAccountsAndDeduplicates() {
        assertThat(scheduler.enqueueDue()).isEqualTo(1);
        assertThat(scheduler.enqueueDue()).isEqualTo(1);
        assertThat(jobs.count()).as("dedup key prevents a second queued sync").isEqualTo(1);
        worker.runOnce();
        assertThat(scheduler.enqueueDue()).as("just synced → not due").isZero();
    }

    @Test
    void inboxApiListsSearchesAndIsolatesWorkspaces() throws Exception {
        box.add("m1", TestEmails.message().text(TestEmails.JAVA_JD).bytes(), "INBOX")
           .add("m2", TestEmails.message().from("someone@unknown.example").subject("Lunch?").text("hi").bytes(), "INBOX");
        sync.sync(account.getId());
        mvc.perform(get("/api/v1/emails").with(as(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get("/api/v1/emails?decision=ALLOWED").with(as(owner)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].subject").doesNotExist());
        mvc.perform(get("/api/v1/emails?q=lunch").with(as(owner))).andExpect(jsonPath("$.items[0].subject").value("Lunch?"));
        var id = email("m1").getId();
        mvc.perform(get("/api/v1/emails/" + id).with(as(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.textBody").value(org.hamcrest.Matchers.containsString("Spring Boot")))
                .andExpect(jsonPath("$.sourceExplanation").value(org.hamcrest.Matchers.containsString("vendor-a.example")));

        AppUser other = otherWorkspaceUser();
        mvc.perform(get("/api/v1/emails").with(as(other))).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/emails/" + id).with(as(other))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/gmail/accounts").with(as(other))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/v1/gmail/accounts/" + account.getId() + "/sync").with(as(other)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void accountListNeverExposesTokens() throws Exception {
        mvc.perform(get("/api/v1/gmail/accounts").with(as(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].emailAddress").value("candidate@example.com"))
                .andExpect(jsonPath("$[0].refreshTokenEncrypted").doesNotExist())
                .andExpect(jsonPath("$[0].accessTokenEncrypted").doesNotExist());
        String stored = jdbc.queryForObject("SELECT refresh_token_encrypted FROM gmail_account", String.class);
        assertThat(stored).startsWith("v1:").doesNotContain("refresh");
    }

    @Autowired com.c2cagent.users.WorkspaceRepository workspaces;
    @Autowired com.c2cagent.users.AppUserRepository users;

    private AppUser otherWorkspaceUser() {
        var ws = workspaces.save(new com.c2cagent.users.Workspace("other"));
        return users.save(new AppUser(ws.getId(), "other", "Other", null, "{noop}x", com.c2cagent.users.UserRole.OWNER));
    }
}
