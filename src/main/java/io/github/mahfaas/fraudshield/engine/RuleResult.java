package io.github.mahfaas.fraudshield.engine;

import io.github.mahfaas.fraudshield.model.Verdict;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Result produced by a single rule evaluation.
 */
@Data
@Builder
@AllArgsConstructor
public class RuleResult {

    private final Verdict verdict;
    private final String ruleName;
    private final String reason;
    private final int riskScore;

    public boolean isTriggered() {
        return verdict != Verdict.APPROVED;
    }

    public static RuleResult approve(String ruleName) {
        return new RuleResult(Verdict.APPROVED, ruleName, null, 0);
    }

    public static RuleResult decline(String ruleName, String reason) {
        return new RuleResult(Verdict.DECLINED, ruleName, reason, 100);
    }

    public static RuleResult manualReview(String ruleName, String reason) {
        return new RuleResult(Verdict.MANUAL_REVIEW, ruleName, reason, 50);
    }
}
