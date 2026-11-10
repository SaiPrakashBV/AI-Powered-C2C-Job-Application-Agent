package com.c2cagent.email;

import java.time.Instant;
import java.util.List;

/** Result of parsing one raw RFC 822 message. Bodies are untrusted text; HTML is already sanitized. */
public record ParsedEmail(String fromAddress, String fromName, String replyTo, String to, String cc, String subject,
                          String messageId, String inReplyTo, String references, Instant sentDate, String textBody,
                          String htmlSanitized, String htmlText, List<Attachment> attachments, List<String> warnings) {

    /** {@code content} is null when the part exceeded the size limit or could not be decoded. */
    public record Attachment(String originalFilename, String safeFilename, String contentType, int sizeBytes,
                             byte[] content, boolean inline, boolean tooLarge, String error) {}
}
