package com.c2cagent.email;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SourceRuleDtos {
    private SourceRuleDtos() {}

    public record RuleDto(UUID id, SourceRuleType ruleType, String matchValue, UUID gmailAccountId, int priority,
                          boolean enabled, String note, Instant createdAt, Instant updatedAt) {
        static RuleDto from(EmailSourceRule r) {
            return new RuleDto(r.getId(), r.getRuleType(), r.getMatchValue(), r.getGmailAccountId(), r.getPriority(),
                    r.isEnabled(), r.getNote(), r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    public record RuleRequest(@NotNull SourceRuleType ruleType, @NotBlank @Size(max = 320) String matchValue,
                              UUID gmailAccountId, @Min(0) @Max(10000) Integer priority, Boolean enabled,
                              @Size(max = 500) String note) {}

    public record ImportRequest(@NotNull @Size(max = 5000) List<@Valid RuleRequest> rules) {}

    public record ImportResult(int created, int skippedDuplicates, List<String> errors) {}

    public record EvaluateRequest(@NotBlank @Size(max = 500) String fromAddress, List<String> labelIds,
                                  UUID gmailAccountId) {}

    public record EvaluateResponse(SourceDecision decision, UUID ruleId, String explanation,
                                   boolean continuesToClassification) {}

    public static final String PRECEDENCE = """
            1. Exact address rules: blocked address, then approved address / trusted forwarder.
            2. Domain rules: the most specific matching domain wins; at equal specificity a block beats an approval.
            3. Gmail label rules: blocked label, then approved label.
            4. Nothing matched: discovery mode on → evaluate as possible new recruiter; off → not processed.
            Within a step: lower priority number first, then account-specific before global rules.
            An approved sender only makes a message eligible: every message still goes through content classification.""";
}
