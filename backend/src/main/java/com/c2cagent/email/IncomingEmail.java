package com.c2cagent.email;

import com.c2cagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/** Original received message content as synced from Gmail. Derived data (classification etc.) lives elsewhere. */
@Entity
@Table(name = "incoming_email")
public class IncomingEmail extends BaseEntity {
    @Column(name = "workspace_id", nullable = false) private UUID workspaceId;
    @Column(name = "gmail_account_id", nullable = false) private UUID gmailAccountId;
    @Column(name = "gmail_message_id", nullable = false, length = 100) private String gmailMessageId;
    @Column(name = "gmail_thread_id", length = 100) private String gmailThreadId;
    @Column(name = "history_id", precision = 30) private BigInteger historyId;
    @Column(name = "label_ids", length = 2000) private String labelIds;
    @Column(name = "internal_date") private Instant internalDate;
    @Column(name = "from_address", length = 320) private String fromAddress;
    @Column(name = "from_name", length = 500) private String fromName;
    @Column(name = "reply_to", length = 1000) private String replyTo;
    @Column(name = "to_addresses") private String toAddresses;
    @Column(name = "cc_addresses") private String ccAddresses;
    @Column(length = 2000) private String subject;
    @Column(name = "message_id_header", length = 1000) private String messageIdHeader;
    @Column(name = "in_reply_to", length = 1000) private String inReplyTo;
    @Column(name = "references_header") private String referencesHeader;
    @Column(name = "raw_size_bytes") private Integer rawSizeBytes;
    @Column(name = "text_body") private String textBody;
    @Column(name = "html_body_sanitized") private String htmlBodySanitized;
    @Column(name = "html_text") private String htmlText;
    @Column(length = 500) private String snippet;
    @Column(name = "is_own_sent", nullable = false) private boolean ownSent;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_decision", nullable = false, length = 20) private SourceDecision sourceDecision;
    @Column(name = "source_rule_id") private UUID sourceRuleId;
    @Column(name = "source_explanation", length = 1000) private String sourceExplanation;
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 30) private ProcessingStatus processingStatus;
    @Column(name = "parse_warnings") private String parseWarnings;

    protected IncomingEmail() {}

    public IncomingEmail(UUID workspaceId, UUID gmailAccountId, String gmailMessageId) {
        this.workspaceId = workspaceId;
        this.gmailAccountId = gmailAccountId;
        this.gmailMessageId = gmailMessageId;
    }

    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getGmailAccountId() { return gmailAccountId; }
    public String getGmailMessageId() { return gmailMessageId; }
    public String getGmailThreadId() { return gmailThreadId; }
    public void setGmailThreadId(String v) { gmailThreadId = v; }
    public BigInteger getHistoryId() { return historyId; }
    public void setHistoryId(BigInteger v) { historyId = v; }
    public String getLabelIds() { return labelIds; }
    public void setLabelIds(String v) { labelIds = v; }
    public Instant getInternalDate() { return internalDate; }
    public void setInternalDate(Instant v) { internalDate = v; }
    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String v) { fromAddress = v; }
    public String getFromName() { return fromName; }
    public void setFromName(String v) { fromName = v; }
    public String getReplyTo() { return replyTo; }
    public void setReplyTo(String v) { replyTo = v; }
    public String getToAddresses() { return toAddresses; }
    public void setToAddresses(String v) { toAddresses = v; }
    public String getCcAddresses() { return ccAddresses; }
    public void setCcAddresses(String v) { ccAddresses = v; }
    public String getSubject() { return subject; }
    public void setSubject(String v) { subject = v; }
    public String getMessageIdHeader() { return messageIdHeader; }
    public void setMessageIdHeader(String v) { messageIdHeader = v; }
    public String getInReplyTo() { return inReplyTo; }
    public void setInReplyTo(String v) { inReplyTo = v; }
    public String getReferencesHeader() { return referencesHeader; }
    public void setReferencesHeader(String v) { referencesHeader = v; }
    public Integer getRawSizeBytes() { return rawSizeBytes; }
    public void setRawSizeBytes(Integer v) { rawSizeBytes = v; }
    public String getTextBody() { return textBody; }
    public void setTextBody(String v) { textBody = v; }
    public String getHtmlBodySanitized() { return htmlBodySanitized; }
    public void setHtmlBodySanitized(String v) { htmlBodySanitized = v; }
    public String getHtmlText() { return htmlText; }
    public void setHtmlText(String v) { htmlText = v; }
    public String getSnippet() { return snippet; }
    public void setSnippet(String v) { snippet = v; }
    public boolean isOwnSent() { return ownSent; }
    public void setOwnSent(boolean v) { ownSent = v; }
    public SourceDecision getSourceDecision() { return sourceDecision; }
    public void setSourceDecision(SourceDecision v) { sourceDecision = v; }
    public UUID getSourceRuleId() { return sourceRuleId; }
    public void setSourceRuleId(UUID v) { sourceRuleId = v; }
    public String getSourceExplanation() { return sourceExplanation; }
    public void setSourceExplanation(String v) { sourceExplanation = v; }
    public ProcessingStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(ProcessingStatus v) { processingStatus = v; }
    public String getParseWarnings() { return parseWarnings; }
    public void setParseWarnings(String v) { parseWarnings = v; }
}
