package com.c2cagent.email;

import com.c2cagent.audit.AuditService;
import com.c2cagent.auth.CurrentUser;
import com.c2cagent.common.ApiException;
import com.c2cagent.gmail.GmailAccountRepository;
import com.c2cagent.users.SettingsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceRuleService {
    private final EmailSourceRuleRepository repository;
    private final GmailAccountRepository accounts;
    private final SettingsService settings;
    private final AuditService audit;

    public SourceRuleService(EmailSourceRuleRepository repository, GmailAccountRepository accounts,
                             SettingsService settings, AuditService audit) {
        this.repository = repository;
        this.accounts = accounts;
        this.settings = settings;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<EmailSourceRule> list(UUID workspaceId) {
        return repository.findByWorkspaceIdOrderByPriorityAscCreatedAtAsc(workspaceId);
    }

    @Transactional
    public EmailSourceRule create(CurrentUser user, SourceRuleDtos.RuleRequest req) {
        String value = validate(user, req);
        if (isDuplicate(user.workspaceId(), req.ruleType(), value, req.gmailAccountId(), null)) {
            throw ApiException.conflict("DUPLICATE_RULE", "An identical rule already exists");
        }
        EmailSourceRule rule = repository.save(new EmailSourceRule(user.workspaceId(), req.ruleType(), value,
                req.gmailAccountId(), req.priority() == null ? 100 : req.priority(),
                req.enabled() == null || req.enabled(), blankToNull(req.note())));
        audit.recordUser(user, "SOURCE_RULE_CREATED", "email_source_rule", rule.getId().toString(),
                "Source rule created: " + rule.getRuleType() + " " + rule.getMatchValue(), Map.of());
        return rule;
    }

    @Transactional
    public EmailSourceRule update(CurrentUser user, UUID id, SourceRuleDtos.RuleRequest req) {
        EmailSourceRule rule = repository.findByIdAndWorkspaceId(id, user.workspaceId())
                .orElseThrow(() -> ApiException.notFound("Source rule"));
        String value = validate(user, req);
        if (isDuplicate(user.workspaceId(), req.ruleType(), value, req.gmailAccountId(), id)) {
            throw ApiException.conflict("DUPLICATE_RULE", "An identical rule already exists");
        }
        rule.setRuleType(req.ruleType());
        rule.setMatchValue(value);
        rule.setGmailAccountId(req.gmailAccountId());
        if (req.priority() != null) {
            rule.setPriority(req.priority());
        }
        if (req.enabled() != null) {
            rule.setEnabled(req.enabled());
        }
        rule.setNote(blankToNull(req.note()));
        audit.recordUser(user, "SOURCE_RULE_UPDATED", "email_source_rule", id.toString(),
                "Source rule updated: " + rule.getRuleType() + " " + value + (rule.isEnabled() ? "" : " (disabled)"),
                Map.of());
        return repository.saveAndFlush(rule);
    }

    @Transactional
    public void delete(CurrentUser user, UUID id) {
        EmailSourceRule rule = repository.findByIdAndWorkspaceId(id, user.workspaceId())
                .orElseThrow(() -> ApiException.notFound("Source rule"));
        repository.delete(rule);
        audit.recordUser(user, "SOURCE_RULE_DELETED", "email_source_rule", id.toString(),
                "Source rule deleted: " + rule.getRuleType() + " " + rule.getMatchValue(), Map.of());
    }

    @Transactional
    public SourceRuleDtos.ImportResult importRules(CurrentUser user, List<SourceRuleDtos.RuleRequest> rules) {
        int created = 0;
        int dup = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            SourceRuleDtos.RuleRequest r = rules.get(i);
            try {
                String value = validate(user, r);
                if (isDuplicate(user.workspaceId(), r.ruleType(), value, r.gmailAccountId(), null)) {
                    dup++;
                    continue;
                }
                repository.save(new EmailSourceRule(user.workspaceId(), r.ruleType(), value, r.gmailAccountId(),
                        r.priority() == null ? 100 : r.priority(), r.enabled() == null || r.enabled(),
                        blankToNull(r.note())));
                created++;
            } catch (ApiException e) {
                errors.add("Rule " + (i + 1) + ": " + e.getMessage());
            }
        }
        audit.recordUser(user, "SOURCE_RULES_IMPORTED", "email_source_rule", null,
                "Imported " + created + " source rule(s)", Map.of("created", created, "duplicates", dup,
                        "errors", errors.size()));
        return new SourceRuleDtos.ImportResult(created, dup, errors);
    }

    @Transactional(readOnly = true)
    public SourceRuleEngine.Result evaluate(UUID workspaceId, String from, List<String> labels, UUID accountId) {
        boolean discovery = settings.getOrCreate(workspaceId).isSourceDiscoveryMode();
        return SourceRuleEngine.evaluate(new SourceRuleEngine.Input(from, labels, accountId, discovery),
                repository.findByWorkspaceIdAndEnabledTrue(workspaceId));
    }

    private String validate(CurrentUser user, SourceRuleDtos.RuleRequest req) {
        String value = SourceRuleEngine.normalizeRuleValue(req.ruleType(), req.matchValue());
        if (value == null) {
            String expected = req.ruleType().isAddress() ? "an email address (name@vendor.com)"
                    : req.ruleType().isDomain() ? "a domain (vendor.com or @vendor.com)" : "a Gmail label id";
            throw ApiException.badRequest("INVALID_RULE_VALUE", "'" + req.matchValue() + "' is not " + expected);
        }
        if (req.gmailAccountId() != null
                && accounts.findByIdAndWorkspaceId(req.gmailAccountId(), user.workspaceId()).isEmpty()) {
            throw ApiException.badRequest("UNKNOWN_GMAIL_ACCOUNT", "Gmail account not found");
        }
        return value;
    }

    private boolean isDuplicate(UUID ws, SourceRuleType type, String value, UUID accountId, UUID excludeId) {
        return repository.findByWorkspaceIdOrderByPriorityAscCreatedAtAsc(ws).stream()
                .anyMatch(r -> !r.getId().equals(excludeId) && r.getRuleType() == type
                        && r.getMatchValue().equals(value) && Objects.equals(r.getGmailAccountId(), accountId)
                        && r.getProfileId() == null);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
