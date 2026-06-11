package io.github.mahfaas.fraudshield.engine.rules;

import io.github.mahfaas.fraudshield.engine.Rule;
import io.github.mahfaas.fraudshield.engine.RuleResult;
import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.Verdict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Composite risk scoring rule — the final safety net of the rule chain.
 *
 * <h3>Why this matters</h3>
 * <p>
 * Individual rules each set a {@code riskScore} on their result (0, 50, or 100).
 * The {@link io.github.mahfaas.fraudshield.engine.RuleEngine} accumulates a
 * {@code totalRiskScore} across all rules. Without this rule, a transaction that
 * triggers three "soft" warnings (e.g. unusual time + geo velocity + elevated amount)
 * would still pass as {@code APPROVED} — each rule alone was not decisive.
 * </p>
 *
 * <h3>How it works</h3>
 * <p>
 * This rule runs <strong>last</strong> (order=50). It receives an already-evaluated
 * transaction but inspects the <em>accumulated</em> risk score by re-summing all
 * prior rule contributions passed through the {@link RuleContext}:
 * </p>
 * <ul>
 *   <li>{@code totalScore >= hardThreshold} → {@code DECLINED}</li>
 *   <li>{@code totalScore >= softThreshold} → {@code MANUAL_REVIEW}
 *       (only if current verdict is {@code APPROVED}; does not downgrade a
 *       {@code DECLINED} verdict)</li>
 *   <li>Below both thresholds → {@code APPROVED} (no-op pass-through)</li>
 * </ul>
 *
 * <h3>Interview talking point</h3>
 * <p>
 * This transforms a purely binary rule-chain into a <em>weighted risk scoring system</em>
 * — the same concept used in real banking fraud engines. Instead of each rule voting
 * independently, the composite score captures the combined signal of multiple weak
 * indicators that would otherwise be individually below the alert threshold.
 * </p>
 */
@Slf4j
@Component
public class RiskScoreThresholdRule implements Rule {

    public static final String RULE_NAME = "RISK_SCORE_THRESHOLD";

    /**
     * Score at or above which an {@code APPROVED} verdict is escalated to
     * {@code MANUAL_REVIEW}. Does not affect already-DECLINED transactions.
     */
    private final AtomicInteger softThreshold;

    /**
     * Score at or above which the verdict is escalated to {@code DECLINED},
     * regardless of the current verdict.
     */
    private final AtomicInteger hardThreshold;

    public RiskScoreThresholdRule(
            @Value("${fraud.rules.risk-score.soft-threshold:60}") int softThreshold,
            @Value("${fraud.rules.risk-score.hard-threshold:90}") int hardThreshold) {
        this.softThreshold = new AtomicInteger(softThreshold);
        this.hardThreshold = new AtomicInteger(hardThreshold);
        log.info("RiskScoreThresholdRule initialized: softThreshold={}, hardThreshold={}",
                softThreshold, hardThreshold);
    }

    /**
     * Evaluates the accumulated risk by re-reading the score passed via a
     * {@link RuleContext} thread-local. If no context is set (e.g. in unit tests),
     * the rule is a no-op — it gracefully returns {@code APPROVED}.
     *
     * <p><strong>Important:</strong> the RuleEngine populates the context <em>before</em>
     * invoking this rule by calling {@link RuleContext#set(int, Verdict)}.
     */
    @Override
    public RuleResult evaluate(Transaction transaction) {
        RuleContext ctx = RuleContext.current();

        if (ctx == null) {
            // Defensive: context not set (e.g. standalone unit test without RuleEngine)
            log.debug("RiskScoreThresholdRule: no RuleContext found, skipping composite scoring.");
            return RuleResult.approve(RULE_NAME);
        }

        int totalScore = ctx.getAccumulatedScore();
        Verdict priorVerdict = ctx.getPriorVerdict();

        log.debug("RiskScoreThresholdRule: txId={} totalScore={} priorVerdict={}",
                transaction.getTransactionId(), totalScore, priorVerdict);

        // Hard threshold → always DECLINE regardless of prior verdict
        if (totalScore >= hardThreshold.get()) {
            return RuleResult.decline(RULE_NAME,
                    String.format("Composite risk score %d exceeds hard threshold %d — transaction DECLINED",
                            totalScore, hardThreshold.get()));
        }

        // Soft threshold → escalate APPROVED to MANUAL_REVIEW only
        if (totalScore >= softThreshold.get() && priorVerdict == Verdict.APPROVED) {
            return RuleResult.manualReview(RULE_NAME,
                    String.format("Composite risk score %d exceeds soft threshold %d — flagged for review",
                            totalScore, softThreshold.get()));
        }

        // Below soft threshold or already a stronger verdict — no additional signal
        return RuleResult.approve(RULE_NAME);
    }

    @Override
    public String getName() {
        return RULE_NAME;
    }

    /**
     * Order 50 — must run <em>after</em> all other rules so that
     * {@link RuleContext} holds the full accumulated score.
     */
    @Override
    public int getOrder() {
        return 50;
    }

    // ── Getters / setters for runtime config updates ─────────────────────────

    public int getSoftThreshold() {
        return softThreshold.get();
    }

    public int getHardThreshold() {
        return hardThreshold.get();
    }

    public void setSoftThreshold(int value) {
        this.softThreshold.set(value);
        log.info("RiskScoreThresholdRule: updated softThreshold to {}", value);
    }

    public void setHardThreshold(int value) {
        this.hardThreshold.set(value);
        log.info("RiskScoreThresholdRule: updated hardThreshold to {}", value);
    }
}
