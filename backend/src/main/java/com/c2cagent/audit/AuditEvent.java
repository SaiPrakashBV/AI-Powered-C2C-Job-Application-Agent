package com.c2cagent.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only audit record. Never store secrets or document contents in {@code details}. */
@Entity
@Table(name = "audit_event")
public class AuditEvent {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "workspace_id")
    private UUID workspaceId;
    @Column(name = "actor_user_id")
    private UUID actorUserId;
    @Column(name = "actor_type", nullable = false, length = 20)
    private String actorType;
    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;
    @Column(name = "entity_type", length = 80)
    private String entityType;
    @Column(name = "entity_id", length = 100)
    private String entityId;
    @Column(nullable = false, length = 500)
    private String summary;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String details;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AuditEvent() {}

    public AuditEvent(UUID workspaceId, UUID actorUserId, String actorType, String eventType, String entityType,
                      String entityId, String summary, String details) {
        this.workspaceId = workspaceId;
        this.actorUserId = actorUserId;
        this.actorType = actorType;
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.summary = summary.length() > 500 ? summary.substring(0, 500) : summary;
        this.details = details;
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getActorUserId() { return actorUserId; }
    public String getActorType() { return actorType; }
    public String getEventType() { return eventType; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getSummary() { return summary; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
