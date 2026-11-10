package com.c2cagent.audit;

import com.c2cagent.auth.CurrentUser;
import com.c2cagent.common.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-events")
@Tag(name = "Audit")
public class AuditController {
    private final AuditEventRepository repository;

    public AuditController(AuditEventRepository repository) { this.repository = repository; }

    public record AuditEventDto(UUID id, String actorType, String eventType, String entityType, String entityId,
                                String summary, String details, Instant createdAt) {
        static AuditEventDto from(AuditEvent e) {
            return new AuditEventDto(e.getId(), e.getActorType(), e.getEventType(), e.getEntityType(),
                    e.getEntityId(), e.getSummary(), e.getDetails(), e.getCreatedAt());
        }
    }

    @GetMapping
    public PageResponse<AuditEventDto> list(CurrentUser user,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "25") int size,
                                            @RequestParam(required = false) String eventType) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 200),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditEvent> result = eventType == null || eventType.isBlank()
                ? repository.findByWorkspaceId(user.workspaceId(), pr)
                : repository.findByWorkspaceIdAndEventType(user.workspaceId(), eventType, pr);
        return PageResponse.of(result, AuditEventDto::from);
    }
}
