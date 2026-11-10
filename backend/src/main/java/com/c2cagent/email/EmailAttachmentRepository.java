package com.c2cagent.email;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailAttachmentRepository extends JpaRepository<EmailAttachment, UUID> {
    List<EmailAttachment> findByIncomingEmailIdOrderByCreatedAtAsc(UUID incomingEmailId);

    Optional<EmailAttachment> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
