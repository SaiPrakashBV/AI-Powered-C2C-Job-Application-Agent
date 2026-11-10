package com.c2cagent.email;

import com.c2cagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "email_source_rule")
public class EmailSourceRule extends BaseEntity {
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 30)
    private SourceRuleType ruleType;
    @Column(name = "match_value", nullable = false, length = 320)
    private String matchValue;
    @Column(name = "gmail_account_id")
    private UUID gmailAccountId;
    @Column(name = "profile_id")
    private UUID profileId;
    @Column(nullable = false)
    private int priority = 100;
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(length = 500)
    private String note;

    protected EmailSourceRule() {}

    public EmailSourceRule(UUID workspaceId, SourceRuleType ruleType, String matchValue, UUID gmailAccountId,
                           int priority, boolean enabled, String note) {
        this.workspaceId = workspaceId;
        this.ruleType = ruleType;
        this.matchValue = matchValue;
        this.gmailAccountId = gmailAccountId;
        this.priority = priority;
        this.enabled = enabled;
        this.note = note;
    }

    public UUID getWorkspaceId() { return workspaceId; }
    public SourceRuleType getRuleType() { return ruleType; }
    public void setRuleType(SourceRuleType t) { this.ruleType = t; }
    public String getMatchValue() { return matchValue; }
    public void setMatchValue(String v) { this.matchValue = v; }
    public UUID getGmailAccountId() { return gmailAccountId; }
    public void setGmailAccountId(UUID id) { this.gmailAccountId = id; }
    public UUID getProfileId() { return profileId; }
    public int getPriority() { return priority; }
    public void setPriority(int p) { this.priority = p; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }
    public String getNote() { return note; }
    public void setNote(String n) { this.note = n; }
}
