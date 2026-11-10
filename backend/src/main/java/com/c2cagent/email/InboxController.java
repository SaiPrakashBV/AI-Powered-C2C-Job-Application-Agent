package com.c2cagent.email;

import com.c2cagent.auth.CurrentUser;
import com.c2cagent.common.ApiException;
import com.c2cagent.common.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read access to synced emails (original content). Classification review arrives in Phase 3. */
@RestController
@RequestMapping("/api/v1/emails")
@Tag(name = "Inbox")
public class InboxController {
    private final IncomingEmailRepository emails;
    private final EmailAttachmentRepository attachments;
    private final EmailIngestionService ingestion;

    public InboxController(IncomingEmailRepository emails, EmailAttachmentRepository attachments,
                           EmailIngestionService ingestion) {
        this.emails = emails;
        this.attachments = attachments;
        this.ingestion = ingestion;
    }

    public record EmailSummary(UUID id, UUID gmailAccountId, Instant internalDate, String fromAddress, String fromName,
                               String subject, String snippet, SourceDecision sourceDecision,
                               ProcessingStatus processingStatus, boolean hasWarnings) {}

    public record AttachmentDto(UUID id, String filename, String contentType, int sizeBytes,
                                AttachmentExtractionStatus extractionStatus, String extractionError, boolean inline,
                                boolean downloadable, int extractedChars) {}

    public record EmailDetail(UUID id, UUID gmailAccountId, String gmailMessageId, String gmailThreadId,
                              Instant internalDate, String fromAddress, String fromName, String replyTo, String to,
                              String cc, String subject, String messageId, String inReplyTo, String labels,
                              SourceDecision sourceDecision, String sourceExplanation, ProcessingStatus processingStatus,
                              String textBody, String htmlText, String parseWarnings, List<AttachmentDto> attachments) {}

    @GetMapping
    public PageResponse<EmailSummary> list(CurrentUser user, @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "25") int size,
                                           @RequestParam(required = false) SourceDecision decision,
                                           @RequestParam(required = false) ProcessingStatus status,
                                           @RequestParam(required = false) UUID accountId,
                                           @RequestParam(required = false) String q) {
        var pr = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 200),
                Sort.by(Sort.Direction.DESC, "internalDate").and(Sort.by(Sort.Direction.DESC, "createdAt")));
        String like = q == null || q.isBlank() ? null : "%" + q.strip().toLowerCase() + "%";
        return PageResponse.of(emails.search(user.workspaceId(), decision, status, accountId, like, pr),
                e -> new EmailSummary(e.getId(), e.getGmailAccountId(), e.getInternalDate(), e.getFromAddress(),
                        e.getFromName(), e.getSubject(), e.getSnippet(), e.getSourceDecision(), e.getProcessingStatus(),
                        e.getParseWarnings() != null));
    }

    @GetMapping("/{id}")
    public EmailDetail get(CurrentUser user, @PathVariable UUID id) {
        IncomingEmail e = emails.findByIdAndWorkspaceId(id, user.workspaceId()).orElseThrow(() -> ApiException.notFound("Email"));
        var atts = attachments.findByIncomingEmailIdOrderByCreatedAtAsc(id).stream()
                .map(a -> new AttachmentDto(a.getId(), a.getSafeFilename(), a.getContentType(), a.getSizeBytes(),
                        a.getExtractionStatus(), a.getExtractionError(), a.isInline(), a.getStoragePath() != null,
                        a.getExtractedText() == null ? 0 : a.getExtractedText().length()))
                .toList();
        return new EmailDetail(e.getId(), e.getGmailAccountId(), e.getGmailMessageId(), e.getGmailThreadId(),
                e.getInternalDate(), e.getFromAddress(), e.getFromName(), e.getReplyTo(), e.getToAddresses(),
                e.getCcAddresses(), e.getSubject(), e.getMessageIdHeader(), e.getInReplyTo(), e.getLabelIds(),
                e.getSourceDecision(), e.getSourceExplanation(), e.getProcessingStatus(), e.getTextBody(),
                e.getHtmlText(), e.getParseWarnings(), atts);
    }

    /** Downloads are always served as attachments (never rendered inline) to avoid executing untrusted content. */
    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> download(CurrentUser user, @PathVariable UUID attachmentId) {
        EmailAttachment a = attachments.findByIdAndWorkspaceId(attachmentId, user.workspaceId())
                .orElseThrow(() -> ApiException.notFound("Attachment"));
        if (a.getStoragePath() == null) throw ApiException.notFound("Stored attachment file");
        var path = ingestion.resolveStoredFile(a.getStoragePath());
        if (!Files.isRegularFile(path)) throw ApiException.notFound("Stored attachment file");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(a.getSafeFilename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
    }
}
