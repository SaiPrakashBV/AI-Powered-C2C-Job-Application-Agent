package com.c2cagent.email;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Pure, deterministic evaluation of sender/source rules. <b>Precedence</b> (documented in the UI and tested):
 * <ol>
 *   <li>Exact address rules: BLOCK_ADDRESS, then ALLOW_ADDRESS / TRUSTED_FORWARDER.</li>
 *   <li>Domain rules: the most specific matching domain wins (sub.vendor.com beats vendor.com);
 *       at equal specificity BLOCK_DOMAIN beats ALLOW_DOMAIN.</li>
 *   <li>Gmail label rules: BLOCK_LABEL, then ALLOW_LABEL.</li>
 *   <li>No rule matched: DISCOVERY if discovery mode is on, otherwise UNLISTED.</li>
 * </ol>
 * Inside one step, rules are ordered by priority (lower number first), then account-specific before global.
 * Only enabled rules that are global or scoped to the message's Gmail account apply.
 */
public final class SourceRuleEngine {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern DOMAIN = Pattern.compile("^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");

    private SourceRuleEngine() {}

    public record Input(String fromAddress, Collection<String> labelIds, UUID gmailAccountId, boolean discoveryMode) {}

    public record Result(SourceDecision decision, UUID ruleId, String explanation) {}

    public static Result evaluate(Input in, List<EmailSourceRule> rules) {
        String from = normalizeAddress(in.fromAddress());
        String domain = from == null ? null : from.substring(from.indexOf('@') + 1);
        List<EmailSourceRule> applicable = rules.stream()
                .filter(EmailSourceRule::isEnabled)
                .filter(r -> r.getGmailAccountId() == null || r.getGmailAccountId().equals(in.gmailAccountId()))
                .filter(r -> r.getProfileId() == null) // profile-specific rules are applied per profile (Phase 4)
                .sorted(Comparator.comparingInt(EmailSourceRule::getPriority)
                        .thenComparing(r -> r.getGmailAccountId() == null ? 1 : 0))
                .toList();

        if (from != null) {
            Optional<EmailSourceRule> block = first(applicable, SourceRuleType.BLOCK_ADDRESS, from);
            if (block.isPresent()) {
                return result(SourceDecision.BLOCKED, block.get(), "Sender address " + from + " is blocked");
            }
            Optional<EmailSourceRule> allow = applicable.stream()
                    .filter(r -> (r.getRuleType() == SourceRuleType.ALLOW_ADDRESS
                            || r.getRuleType() == SourceRuleType.TRUSTED_FORWARDER) && r.getMatchValue().equals(from))
                    .findFirst();
            if (allow.isPresent()) {
                String why = allow.get().getRuleType() == SourceRuleType.TRUSTED_FORWARDER
                        ? "Sender " + from + " is a trusted forwarder; the original recruiter is identified during extraction"
                        : "Sender address " + from + " is approved";
                return result(SourceDecision.ALLOWED, allow.get(), why + " (content classification still required)");
            }
            EmailSourceRule best = null;
            for (EmailSourceRule r : applicable) {
                if (!r.getRuleType().isDomain() || !domainMatches(domain, r.getMatchValue())) {
                    continue;
                }
                if (best == null || r.getMatchValue().length() > best.getMatchValue().length()
                        || (r.getMatchValue().length() == best.getMatchValue().length()
                            && r.getRuleType().isBlock() && !best.getRuleType().isBlock())) {
                    best = r;
                }
            }
            if (best != null) {
                return best.getRuleType().isBlock()
                        ? result(SourceDecision.BLOCKED, best, "Sender domain matches blocked domain " + best.getMatchValue())
                        : result(SourceDecision.ALLOWED, best, "Sender domain matches approved domain "
                                + best.getMatchValue() + " (content classification still required)");
            }
        }
        if (in.labelIds() != null && !in.labelIds().isEmpty()) {
            List<String> labels = in.labelIds().stream().map(l -> l.toLowerCase(Locale.ROOT)).toList();
            Optional<EmailSourceRule> bl = applicable.stream()
                    .filter(r -> r.getRuleType() == SourceRuleType.BLOCK_LABEL && labels.contains(r.getMatchValue()))
                    .findFirst();
            if (bl.isPresent()) {
                return result(SourceDecision.BLOCKED, bl.get(), "Gmail label " + bl.get().getMatchValue() + " is blocked");
            }
            Optional<EmailSourceRule> al = applicable.stream()
                    .filter(r -> r.getRuleType() == SourceRuleType.ALLOW_LABEL && labels.contains(r.getMatchValue()))
                    .findFirst();
            if (al.isPresent()) {
                return result(SourceDecision.ALLOWED, al.get(), "Gmail label " + al.get().getMatchValue()
                        + " is approved (content classification still required)");
            }
        }
        return in.discoveryMode()
                ? new Result(SourceDecision.DISCOVERY, null, "No rule matched; discovery mode is on, so the message is evaluated as a possible new recruiter")
                : new Result(SourceDecision.UNLISTED, null, "No rule matched and discovery mode is off; message is not processed");
    }

    private static Optional<EmailSourceRule> first(List<EmailSourceRule> rules, SourceRuleType type, String value) {
        return rules.stream().filter(r -> r.getRuleType() == type && r.getMatchValue().equals(value)).findFirst();
    }

    private static Result result(SourceDecision d, EmailSourceRule r, String why) {
        return new Result(d, r.getId(), why);
    }

    static boolean domainMatches(String senderDomain, String ruleDomain) {
        return senderDomain != null && (senderDomain.equals(ruleDomain) || senderDomain.endsWith("." + ruleDomain));
    }

    public static String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        String a = address.strip().toLowerCase(Locale.ROOT);
        int lt = a.lastIndexOf('<');
        int gt = a.lastIndexOf('>');
        if (lt >= 0 && gt > lt) {
            a = a.substring(lt + 1, gt).strip();
        }
        return EMAIL.matcher(a).matches() ? a : null;
    }

    /** Validates and normalizes a rule value for its type; returns null when invalid. */
    public static String normalizeRuleValue(SourceRuleType type, String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.strip().toLowerCase(Locale.ROOT);
        if (type.isAddress()) {
            return normalizeAddress(v);
        }
        if (type.isDomain()) {
            if (v.startsWith("*.")) {
                v = v.substring(2);
            }
            if (v.startsWith("@")) {
                v = v.substring(1);
            }
            return DOMAIN.matcher(v).matches() ? v : null;
        }
        return !v.isEmpty() && v.length() <= 100 ? v : null;
    }
}
