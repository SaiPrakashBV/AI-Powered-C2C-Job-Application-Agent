package com.c2cagent.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    Page<AuditEvent> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    Page<AuditEvent> findByWorkspaceIdAndEventType(UUID workspaceId, String eventType, Pageable pageable);

    List<AuditEvent> findTop10ByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
}
