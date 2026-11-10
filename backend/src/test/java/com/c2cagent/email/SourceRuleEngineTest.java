package com.c2cagent.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceRuleEngineTest {
    private static final UUID WS = UUID.randomUUID();
    private static final UUID ACCOUNT = UUID.randomUUID();

    private static EmailSourceRule rule(SourceRuleType t, String v, int priority) {
        return new EmailSourceRule(WS, t, SourceRuleEngine.normalizeRuleValue(t, v), null, priority, true, null);
    }

    private static SourceRuleEngine.Result eval(String from, boolean discovery, EmailSourceRule... rules) {
        return SourceRuleEngine.evaluate(new SourceRuleEngine.Input(from, List.of("INBOX"), ACCOUNT, discovery), List.of(rules));
    }

    @Test
    void exactApprovedAddressIsAllowedButStillNeedsClassification() {
        var r = eval("Alex <ALEX@Vendor-A.com>", false, rule(SourceRuleType.ALLOW_ADDRESS, "alex@vendor-a.com", 100));
        assertThat(r.decision()).isEqualTo(SourceDecision.ALLOWED);
        assertThat(r.explanation()).contains("content classification still required");
        assertThat(r.decision().continuesToClassification()).isTrue();
    }

    @Test
    void blockedAddressBeatsApprovedDomain() {
        var r = eval("newsletters@staffing.com", false, rule(SourceRuleType.ALLOW_DOMAIN, "@staffing.com", 1),
                rule(SourceRuleType.BLOCK_ADDRESS, "newsletters@staffing.com", 500));
        assertThat(r.decision()).isEqualTo(SourceDecision.BLOCKED);
    }

    @Test
    void approvedAddressBeatsBlockedDomain() {
        var r = eval("jobs@promo.com", false, rule(SourceRuleType.BLOCK_DOMAIN, "promo.com", 1),
                rule(SourceRuleType.ALLOW_ADDRESS, "jobs@promo.com", 900));
        assertThat(r.decision()).isEqualTo(SourceDecision.ALLOWED);
    }

    @Test
    void mostSpecificDomainWinsAndBlockWinsTies() {
        EmailSourceRule allowParent = rule(SourceRuleType.ALLOW_DOMAIN, "staffing.com", 100);
        EmailSourceRule blockSub = rule(SourceRuleType.BLOCK_DOMAIN, "marketing.staffing.com", 100);
        assertThat(eval("x@marketing.staffing.com", false, allowParent, blockSub).decision()).isEqualTo(SourceDecision.BLOCKED);
        assertThat(eval("x@recruit.staffing.com", false, allowParent, blockSub).decision()).isEqualTo(SourceDecision.ALLOWED);
        assertThat(eval("x@staffing.com", false, allowParent, rule(SourceRuleType.BLOCK_DOMAIN, "staffing.com", 999))
                .decision()).isEqualTo(SourceDecision.BLOCKED);
        assertThat(eval("x@notstaffing.com", false, allowParent).decision()).isEqualTo(SourceDecision.UNLISTED);
    }

    @Test
    void unknownSenderDependsOnDiscoveryMode() {
        assertThat(eval("new@unknown.io", false).decision()).isEqualTo(SourceDecision.UNLISTED);
        assertThat(eval("new@unknown.io", true).decision()).isEqualTo(SourceDecision.DISCOVERY);
        assertThat(eval("new@unknown.io", true).decision().continuesToClassification()).isTrue();
    }

    @Test
    void trustedForwarderIsAllowed() {
        var r = eval("me.forward@gmail.com", false, rule(SourceRuleType.TRUSTED_FORWARDER, "me.forward@gmail.com", 100));
        assertThat(r.decision()).isEqualTo(SourceDecision.ALLOWED);
        assertThat(r.explanation()).contains("trusted forwarder");
    }

    @Test
    void labelRulesApplyWhenNoSenderRuleMatches() {
        var r = SourceRuleEngine.evaluate(new SourceRuleEngine.Input("a@b.com", List.of("Label_42"), ACCOUNT, false),
                List.of(rule(SourceRuleType.ALLOW_LABEL, "label_42", 100)));
        assertThat(r.decision()).isEqualTo(SourceDecision.ALLOWED);
    }

    @Test
    void disabledRulesAndOtherAccountsRulesAreIgnored() {
        EmailSourceRule disabled = rule(SourceRuleType.BLOCK_ADDRESS, "a@b.com", 1);
        disabled.setEnabled(false);
        EmailSourceRule otherAccount = new EmailSourceRule(WS, SourceRuleType.ALLOW_ADDRESS, "a@b.com", UUID.randomUUID(), 1, true, null);
        assertThat(eval("a@b.com", false, disabled, otherAccount).decision()).isEqualTo(SourceDecision.UNLISTED);
    }

    @Test
    void missingOrMalformedSenderNeverMatchesSenderRules() {
        assertThat(eval(null, false, rule(SourceRuleType.ALLOW_DOMAIN, "b.com", 1)).decision()).isEqualTo(SourceDecision.UNLISTED);
        assertThat(eval("not-an-address", true).decision()).isEqualTo(SourceDecision.DISCOVERY);
    }

    @Test
    void ruleValuesAreValidatedAndNormalized() {
        assertThat(SourceRuleEngine.normalizeRuleValue(SourceRuleType.ALLOW_DOMAIN, " @Vendor-B.COM ")).isEqualTo("vendor-b.com");
        assertThat(SourceRuleEngine.normalizeRuleValue(SourceRuleType.ALLOW_DOMAIN, "*.vendor.com")).isEqualTo("vendor.com");
        assertThat(SourceRuleEngine.normalizeRuleValue(SourceRuleType.ALLOW_DOMAIN, "not a domain")).isNull();
        assertThat(SourceRuleEngine.normalizeRuleValue(SourceRuleType.ALLOW_ADDRESS, "Jobs@Vendor-B.com")).isEqualTo("jobs@vendor-b.com");
        assertThat(SourceRuleEngine.normalizeRuleValue(SourceRuleType.BLOCK_ADDRESS, "vendor-b.com")).isNull();
    }
}
