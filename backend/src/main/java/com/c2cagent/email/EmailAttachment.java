package com.c2cagent.email;

import com.c2cagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "email_attachment")
public class EmailAttachment extends BaseEntity {
    @Column(name = "workspace_id", nullable = false) private UUID workspaceId;
    @Column(name = "incoming_email_id", nullable = false) private UUID incomingEmailId;
    @Column(name = "original_filename", length = 500) private String originalFilename;
    @Column(name = "safe_filename", nullable = false, length = 255) private String safeFilename;
    @Column(name = "content_type", length = 200) private String contentType;
    @Column(name = "size_bytes", nullable = false) private int sizeBytes;
    @Column(length = 64) private String sha256;
    @Column(name = "storage_path", length = 1000) private String storagePath;
    @Column(name = "is_inline", nullable = false) private boolean inline;
    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 20) private AttachmentExtractionStatus extractionStatus;
    @Column(name = "extracted_text") private String extractedText;
    @Column(name = "extraction_error", length = 1000) private String extractionError;

    protected EmailAttachment() {}

    public EmailAttachment(UUID workspaceId, UUID incomingEmailId, String originalFilename, String safeFilename,
                           String contentType, int sizeBytes, String sha256, boolean inline,
                           AttachmentExtractionStatus status, String extractedText, String error) {
        this.workspaceId = workspaceId;
        this.incomingEmailId = incomingEmailId;
        this.originalFilename = originalFilename;
        this.safeFilename = safeFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.inline = inline;
        this.extractionStatus = status;
        this.extractedText = extractedText;
        this.extractionError = error;
    }

    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getIncomingEmailId() { return incomingEmailId; }
    public String getOriginalFilename() { return originalFilename; }
    public String getSafeFilename() { return safeFilename; }
    public String getContentType() { return contentType; }
    public int getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String p) { storagePath = p; }
    public boolean isInline() { return inline; }
    public AttachmentExtractionStatus getExtractionStatus() { return extractionStatus; }
    public String getExtractedText() { return extractedText; }
    public String getExtractionError() { return extractionError; }
}
