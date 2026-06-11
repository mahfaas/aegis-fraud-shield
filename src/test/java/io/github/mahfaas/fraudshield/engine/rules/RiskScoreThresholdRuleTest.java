package io.github.mahfaas.fraudshield.engine.rules;

import io.github.mahfaas.fraudshield.engine.RuleResult;
import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.Verdict;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RiskScoreThresholdRule}.
 *
 * <h3>Testing strategy</h3>
 * <ul>
 *   <li>Populates {@link RuleContext} manually to simulate what
 *       {@link io.github.mahfaas.fraudshield.engine.RuleEngine} does at runtime.</li>
 *   <li>{@link ParameterizedTest} + {@link MethodSource} covers all threshold
 *       boundary combinations.</li>
 *   <li>No mocks needed — the rule is purely functional given a RuleContext.</li>
 * </ul>
 */
class RiskScoreThresholdRuleTest {

    /** soft=60, hard=90 — matching application.yaml defaults */
    private RiskScoreThresholdRule rule;

    @BeforeEach
    void setUp() {
        rule = new RiskScoreThresholdRule(60, 90);
    }

    @AfterEach
    void tearDown() {
        // Always clean up the thread-local, just like the RuleEngine does
        RuleContext.clear();
    }

    private Transaction anyTransaction() {
        return Transaction.builder()
                .transactionId("tx-risk-001")
                .accountId("ACC-042")
                .cardBin("411111")
                .amount(BigDecimal.valueOf(1500))
                .currency("USD")
                .country("US")
                .sourceIp("10.0.0.1")
                .timestamp(Instant.now())
                .build();
    }

    // ── No context ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Returns APPROVED with no-op when RuleContext is absent (defensive behaviour)")
    void noContextIsNoOp() {
        // No RuleContext.set() call — simulates standalone invocation
        RuleResult result = rule.evaluate(anyTransaction());
        assertEquals(Verdict.APPROVED, result.getVerdict());
        assertEquals(0, result.getRiskScore());
    }

    // ── Parameterized boundary matrix ──────────────────────────────────────────

    /**
     * Full boundary matrix.
     * Columns: accumulatedScore, priorVerdict, expectedVerdict, description
     */
    static Stream<Arguments> boundaryMatrix() {
        return Stream.of(
                // ── Below soft threshold ─────────────────────────────────────
                Arguments.of(0,  Verdict.APPROVED,      Verdict.APPROVED,      "zero score + APPROVED → no-op"),
                Arguments.of(59, Verdict.APPROVED,      Verdict.APPROVED,      "one below soft + APPROVED → no-op"),
                Arguments.of(50, Verdict.MANUAL_REVIEW, Verdict.APPROVED, "below soft + prior MANUAL_REVIEW → rule no-ops (engine preserves escalation)"),
                Arguments.of(50, Verdict.DECLINED,      Verdict.APPROVED, "below soft + prior DECLINED → rule no-ops (chain would break before reaching this)"),

                // ── At/above soft, below hard ────────────────────────────────
                Arguments.of(60, Verdict.APPROVED,      Verdict.MANUAL_REVIEW, "at soft threshold + APPROVED → escalate to MANUAL_REVIEW"),
                Arguments.of(61, Verdict.APPROVED,      Verdict.MANUAL_REVIEW, "above soft + APPROVED → MANUAL_REVIEW"),
                Arguments.of(89, Verdict.APPROVED,      Verdict.MANUAL_REVIEW, "one below hard + APPROVED → MANUAL_REVIEW"),
                Arguments.of(60, Verdict.MANUAL_REVIEW, Verdict.APPROVED,      "at soft but already MANUAL_REVIEW → rule no-ops (prior escalation wins)"),

                // ── At/above hard threshold ──────────────────────────────────
                Arguments.of(90, Verdict.APPROVED,      Verdict.DECLINED,      "at hard threshold + APPROVED → DECLINED"),
                Arguments.of(90, Verdict.MANUAL_REVIEW, Verdict.DECLINED,      "at hard threshold + MANUAL_REVIEW → DECLINED"),
                Arguments.of(91, Verdict.APPROVED,      Verdict.DECLINED,      "above hard → DECLINED"),
                Arguments.of(200, Verdict.APPROVED,     Verdict.DECLINED,      "very high score → DECLINED")
        );
    }

    @ParameterizedTest(name = "[{index}] score={0} prior={1} → {2} ({3})")
    @MethodSource("boundaryMatrix")
    @DisplayName("Boundary matrix — all threshold combinations")
    void boundaryMatrix(int score, Verdict priorVerdict, Verdict expectedVerdict, String description) {
        RuleContext.set(score, priorVerdict);

        RuleResult result = rule.evaluate(anyTransaction());

        assertEquals(expectedVerdict, result.getVerdict(),
                "Failed for: " + description + " (score=" + score + ", prior=" + priorVerdict + ")");
    }

    // ── Risk score on the result itself ────────────────────────────────────────

    @Test
    @DisplayName("DECLINED result carries riskScore=100")
    void declinedResultHasRiskScore100() {
        RuleContext.set(90, Verdict.APPROVED);
        RuleResult result = rule.evaluate(anyTransaction());
        assertEquals(Verdict.DECLINED, result.getVerdict());
        assertEquals(100, result.getRiskScore());
    }

    @Test
    @DisplayName("MANUAL_REVIEW result carries riskScore=50")
    void manualReviewResultHasRiskScore50() {
        RuleContext.set(60, Verdict.APPROVED);
        RuleResult result = rule.evaluate(anyTransaction());
        assertEquals(Verdict.MANUAL_REVIEW, result.getVerdict());
        assertEquals(50, result.getRiskScore());
    }

    @Test
    @DisplayName("APPROVED result carries riskScore=0")
    void approvedResultHasRiskScore0() {
        RuleContext.set(0, Verdict.APPROVED);
        RuleResult result = rule.evaluate(anyTransaction());
        assertEquals(Verdict.APPROVED, result.getVerdict());
        assertEquals(0, result.getRiskScore());
    }

    // ── Reason messages ────────────────────────────────────────────────────────

    @Test
    @DisplayName("DECLINED result includes composite score in reason string")
    void declinedReasonIncludesScore() {
        RuleContext.set(95, Verdict.APPROVED);
        RuleResult result = rule.evaluate(anyTransaction());
        assertNotNull(result.getReason());
        assertTrue(result.getReason().contains("95"),
                "Reason should mention the actual score: " + result.getReason());
    }

    @Test
    @DisplayName("MANUAL_REVIEW result includes composite score in reason string")
    void reviewReasonIncludesScore() {
        RuleContext.set(75, Verdict.APPROVED);
        RuleResult result = rule.evaluate(anyTransaction());
        assertNotNull(result.getReason());
        assertTrue(result.getReason().contains("75"),
                "Reason should mention the actual score: " + result.getReason());
    }

    // ── Metadata ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Rule name is RISK_SCORE_THRESHOLD")
    void ruleName() {
        assertEquals("RISK_SCORE_THRESHOLD", rule.getName());
    }

    @Test
    @DisplayName("Rule order is 50 (runs last)")
    void ruleOrder() {
        assertEquals(50, rule.getOrder());
    }

    // ── Runtime threshold updates ──────────────────────────────────────────────

    @Test
    @DisplayName("Lowering soft threshold immediately changes verdict")
    void runtimeSoftThresholdUpdate() {
        rule.setSoftThreshold(30);
        RuleContext.set(35, Verdict.APPROVED);
        RuleResult result = rule.evaluate(anyTransaction());
        assertEquals(Verdict.MANUAL_REVIEW, result.getVerdict(),
                "After lowering soft threshold to 30, score=35 should trigger MANUAL_REVIEW");
    }

    @Test
    @DisplayName("Raising hard threshold prevents DECLINED at original score")
    void runtimeHardThresholdUpdate() {
        rule.setHardThreshold(150);
        RuleContext.set(95, Verdict.APPROVED); // previously would DECLINE at 90
        RuleResult result = rule.evaluate(anyTransaction());
        // 95 < 150 (new hard), 95 >= 60 (soft) → MANUAL_REVIEW
        assertEquals(Verdict.MANUAL_REVIEW, result.getVerdict(),
                "After raising hard threshold to 150, score=95 should only be MANUAL_REVIEW");
    }
}
