package com.c2cagent.audit;

import com.c2cagent.auth.CurrentUser;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) { this.repository = repository; }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordUser(CurrentUser user, String eventType, String entityType, String entityId, String summary,
                           Map<String, ?> details) {
        repository.save(new AuditEvent(user.workspaceId(), user.userId(), "USER", eventType, entityType, entityId,
                summary, toJson(details)));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSystem(UUID workspaceId, String eventType, String entityType, String entityId, String summary,
                             Map<String, ?> details) {
        repository.save(new AuditEvent(workspaceId, null, "SYSTEM", eventType, entityType, entityId, summary,
                toJson(details)));
    }

    /** Used for events before authentication (e.g. failed logins). Runs in its own transaction so it persists. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAnonymous(UUID workspaceId, String eventType, String summary, Map<String, ?> details) {
        repository.save(new AuditEvent(workspaceId, null, "ANONYMOUS", eventType, null, null, summary,
                toJson(details)));
    }

    private static String toJson(Map<String, ?> details) {
        return details == null || details.isEmpty() ? null : JSON.writeValueAsString(details);
    }
}
