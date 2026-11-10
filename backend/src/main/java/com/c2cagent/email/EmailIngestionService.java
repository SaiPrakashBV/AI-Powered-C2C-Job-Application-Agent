package com.c2cagent.email;

import com.c2cagent.configuration.DataDirectoryInitializer;
import com.c2cagent.gmail.GmailAccount;
import com.c2cagent.gmail.MailboxClient;
import jakarta.mail.MessagingException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores one synced message idempotently (unique per mailbox + Gmail message id), applies source rules and
 * extracts attachments. Bodies and attachment files are kept only for messages that continue to classification;
 * excluded (blocked / unlisted / own sent) messages keep headers and the Gmail snippet only (privacy).
 */
@Service
public class EmailIngestionService {
    private static final Logger log = LoggerFactory.getLogger(EmailIngestionService.class);
    private final IncomingEmailRepository emails;
    private final EmailAttachmentRepository attachments;
    private final MimeEmailParser parser;
    private final AttachmentTextExtractor extractor;
    private final SourceRuleService sourceRules;
    private final Path attachmentRoot;

    public EmailIngestionService(IncomingEmailRepository emails, EmailAttachmentRepository attachments,
                                 MimeEmailParser parser, AttachmentTextExtractor extractor,
                                 SourceRuleService sourceRules, DataDirectoryInitializer dataDir) {
        this.emails = emails;
        this.attachments = attachments;
        this.parser = parser;
        this.extractor = extractor;
        this.sourceRules = sourceRules;
        this.attachmentRoot = dataDir.dataDir().resolve("attachments").toAbsolutePath().normalize();
    }

    public boolean alreadyStored(GmailAccount account, String gmailMessageId) {
        return emails.existsByGmailAccountIdAndGmailMessageId(account.getId(), gmailMessageId);
    }

    /** @return true when a new row was stored, false when the message already existed (idempotent). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean ingest(GmailAccount account, MailboxClient.RawMessage raw) {
        if (alreadyStored(account, raw.id())) {
            return false;
        }
        IncomingEmail e = new IncomingEmail(account.getWorkspaceId(), account.getId(), raw.id());
        e.setGmailThreadId(raw.threadId());
        e.setHistoryId(raw.historyId());
        e.setLabelIds(raw.labelIds() == null ? null : String.join(",", raw.labelIds()));
        e.setInternalDate(raw.internalDate());
        e.setRawSizeBytes(raw.raw().length);
        e.setSnippet(truncate(raw.snippet(), 500));

        ParsedEmail parsed = null;
        List<String> warnings = new ArrayList<>();
        try {
            parsed = parser.parse(raw.raw());
            warnings.addAll(parsed.warnings());
            e.setFromAddress(truncate(lower(parsed.fromAddress()), 320));
            e.setFromName(truncate(parsed.fromName(), 500));
            e.setReplyTo(truncate(parsed.replyTo(), 1000));
            e.setToAddresses(parsed.to());
            e.setCcAddresses(parsed.cc());
            e.setSubject(parsed.subject());
            e.setMessageIdHeader(truncate(parsed.messageId(), 1000));
            e.setInReplyTo(truncate(parsed.inReplyTo(), 1000));
            e.setReferencesHeader(parsed.references());
        } catch (MessagingException | RuntimeException ex) {
            warnings.add("Message could not be parsed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }

        boolean ownSent = (raw.labelIds() != null && raw.labelIds().contains("SENT"))
                || (e.getFromAddress() != null && e.getFromAddress().equalsIgnoreCase(account.getEmailAddress()));
        e.setOwnSent(ownSent);
        if (ownSent) {
            e.setSourceDecision(SourceDecision.OWN_SENT);
            e.setSourceExplanation("Sent from the connected mailbox itself; never treated as a new requirement");
        } else {
            SourceRuleEngine.Result r = sourceRules.evaluate(account.getWorkspaceId(), e.getFromAddress(),
                    raw.labelIds(), account.getId());
            e.setSourceDecision(r.decision());
            e.setSourceRuleId(r.ruleId());
            e.setSourceExplanation(truncate(r.explanation(), 1000));
        }
        boolean keep = e.getSourceDecision().continuesToClassification();
        if (parsed == null) {
            e.setProcessingStatus(keep ? ProcessingStatus.PARSE_ERROR : ProcessingStatus.EXCLUDED);
        } else {
            e.setProcessingStatus(keep ? ProcessingStatus.PENDING_CLASSIFICATION : ProcessingStatus.EXCLUDED);
            if (keep) {
                e.setTextBody(parsed.textBody());
                e.setHtmlBodySanitized(parsed.htmlSanitized());
                e.setHtmlText(parsed.htmlText());
            }
        }
        e.setParseWarnings(warnings.isEmpty() ? null : String.join("\n", warnings));
        try {
            emails.saveAndFlush(e);
        } catch (DataIntegrityViolationException dup) {
            return false; // concurrent duplicate (same message delivered twice)
        }
        if (parsed != null) {
            storeAttachments(e, parsed, keep);
        }
        return true;
    }

    private void storeAttachments(IncomingEmail e, ParsedEmail parsed, boolean keep) {
        int n = 0;
        for (ParsedEmail.Attachment a : parsed.attachments()) {
            n++;
            String sha = a.content() == null ? null : sha256(a.content());
            AttachmentTextExtractor.Result r = !keep
                    ? new AttachmentTextExtractor.Result(AttachmentExtractionStatus.UNSUPPORTED, null, "Message excluded by source rules")
                    : a.inline() ? new AttachmentTextExtractor.Result(AttachmentExtractionStatus.UNSUPPORTED, null, null)
                    : extractor.extract(a);
            EmailAttachment att = new EmailAttachment(e.getWorkspaceId(), e.getId(), truncate(a.originalFilename(), 500),
                    a.safeFilename(), truncate(a.contentType(), 200), a.sizeBytes(), sha, a.inline(), r.status(),
                    r.text(), truncate(r.error(), 1000));
            if (keep && a.content() != null && !a.inline()) {
                att.setStoragePath(writeFile(e, n, a));
            }
            attachments.save(att);
        }
    }

    private String writeFile(IncomingEmail e, int index, ParsedEmail.Attachment a) {
        Path dir = attachmentRoot.resolve(e.getId().toString()).normalize();
        Path file = dir.resolve(index + "-" + a.safeFilename()).normalize();
        if (!file.startsWith(attachmentRoot)) {
            throw new IllegalStateException("Refusing to write outside the attachment directory");
        }
        try {
            Files.createDirectories(dir);
            Files.write(file, a.content());
            return attachmentRoot.relativize(file).toString();
        } catch (IOException ex) {
            log.warn("Could not store attachment for email {}: {}", e.getId(), ex.getMessage());
            throw new UncheckedIOException(ex);
        }
    }

    public Path resolveStoredFile(String relative) {
        Path p = attachmentRoot.resolve(relative).normalize();
        if (!p.startsWith(attachmentRoot)) {
            throw new IllegalArgumentException("Invalid attachment path");
        }
        return p;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String lower(String s) { return s == null ? null : s.toLowerCase(java.util.Locale.ROOT); }

    private static String truncate(String s, int max) { return s == null ? null : (s.length() > max ? s.substring(0, max) : s); }

}
