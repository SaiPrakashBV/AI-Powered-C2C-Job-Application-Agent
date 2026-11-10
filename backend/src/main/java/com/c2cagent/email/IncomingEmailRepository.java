package com.c2cagent.email;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IncomingEmailRepository extends JpaRepository<IncomingEmail, UUID> {
    boolean existsByGmailAccountIdAndGmailMessageId(UUID gmailAccountId, String gmailMessageId);

    Optional<IncomingEmail> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Query("""
            select e from IncomingEmail e where e.workspaceId = :ws
              and (:decision is null or e.sourceDecision = :decision)
              and (:status is null or e.processingStatus = :status)
              and (:accountId is null or e.gmailAccountId = :accountId)
              and (:q is null or lower(coalesce(e.subject, '')) like :q or lower(coalesce(e.fromAddress, '')) like :q
                   or lower(coalesce(e.snippet, '')) like :q)
            """)
    Page<IncomingEmail> search(UUID ws, SourceDecision decision, ProcessingStatus status, UUID accountId, String q,
                               Pageable pageable);

    long countByWorkspaceId(UUID workspaceId);

    long countByWorkspaceIdAndProcessingStatus(UUID workspaceId, ProcessingStatus status);
}
