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

    /** Which verdict this rule suggests. */
    private final Verdict verdict;

    /** Name of the rule that produced this result. */
    private final String ruleName;

    /** Human-readable explanation. */
    private final String reason;

    /** Numeric risk score: 0 (APPROVED), 50 (MANUAL_REVIEW), 100 (DECLINED). */
    private final int riskScore;

    /**
     * @return true if the rule triggered (verdict is not APPROVED).
     */
    public boolean isTriggered() {
        return verdict != Verdict.APPROVED;
    }

    /**
     * Convenience factory for a passing result.
     */
    public static RuleResult approve(String ruleName) {
        return new RuleResult(Verdict.APPROVED, ruleName, null, 0);
    }

    /**
     * Convenience factory for a blocking result.
     */
    public static RuleResult decline(String ruleName, String reason) {
        return new RuleResult(Verdict.DECLINED, ruleName, reason, 100);
    }

    /**
     * Convenience factory for a suspicious result.
     */
    public static RuleResult manualReview(String ruleName, String reason) {
        return new RuleResult(Verdict.MANUAL_REVIEW, ruleName, reason, 50);
    }
}
