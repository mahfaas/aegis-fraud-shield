package io.github.mahfaas.fraudshield.engine;

import io.github.mahfaas.fraudshield.cases.FraudCaseRepository;
import io.github.mahfaas.fraudshield.metrics.FraudMetrics;
import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.Verdict;
import io.github.mahfaas.fraudshield.model.VerdictedTransaction;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RuleEngine}.
 *
 * <h3>Testing techniques demonstrated</h3>
 * <ul>
 *   <li>{@link Spy} on {@link FraudMetrics} — wraps a real object so we can both
 *       call actual methods <em>and</em> verify interactions via
 *       {@code verify(metrics).recordRuleTriggered(anyString())}</li>
 *   <li>{@link ParameterizedTest} + {@link MethodSource} — verdict precedence matrix:
 *       ensures the engine's short-circuit and escalation logic is correct for every
 *       combination of rule verdicts</li>
 *   <li>Anonymous {@link Rule} implementations — lightweight test doubles without
 *       extra test-support classes</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RuleEngineTest {

    /**
     * {@code @Spy} wraps a real {@link FraudMetrics} instance built on a
     * {@link SimpleMeterRegistry}.  Unlike {@code @Mock}, a spy calls the real
     * method unless explicitly stubbed — so counters actually increment, but we can
     * still {@code verify} call counts and arguments.
     */
    @Spy
    private FraudMetrics metrics =
            new FraudMetrics(new SimpleMeterRegistry(), mockRuleEngine(), mockFraudCaseRepository());

    private Transaction baseTransaction() {
        return Transaction.builder()
                .transactionId("tx-001")
                .accountId("ACC-001")
                .cardBin("411111")
                .amount(BigDecimal.valueOf(1000))
                .currency("RUB")
                .country("RU")
                .sourceIp("8.8.8.8")
                .timestamp(Instant.now())
                .build();
    }

    /** Minimal stub used only to satisfy FraudMetrics constructor (Gauge binding). */
    private static RuleEngine mockRuleEngine() {
        RuleEngine stub = mock(RuleEngine.class);
        when(stub.getRuleCount()).thenReturn(0);
        return stub;
    }

    /** Minimal stub used only to satisfy FraudMetrics constructor (SLA-breach Gauge binding). */
    private static FraudCaseRepository mockFraudCaseRepository() {
        return mock(FraudCaseRepository.class);
    }

    private static Rule approveRule(String name, int order) {
        return new Rule() {
            @Override public RuleResult evaluate(Transaction tx) { return RuleResult.approve(name); }
            @Override public String getName()  { return name; }
            @Override public int    getOrder() { return order; }
        };
    }

    private static Rule declineRule(String name, int order) {
        return new Rule() {
            @Override public RuleResult evaluate(Transaction tx) { return RuleResult.decline(name, "blocked"); }
            @Override public String getName()  { return name; }
            @Override public int    getOrder() { return order; }
        };
    }

    private static Rule reviewRule(String name, int order) {
        return new Rule() {
            @Override public RuleResult evaluate(Transaction tx) { return RuleResult.manualReview(name, "suspicious"); }
            @Override public String getName()  { return name; }
            @Override public int    getOrder() { return order; }
        };
    }

    // ── @Spy verification ────────────────────────────────────────────────────

    @Test
    @DisplayName("@Spy: recordRuleTriggered() is called once per triggered rule")
    void spyVerifiesRuleTriggeredCallCount() {
        Rule r1 = declineRule("R1", 1);
        RuleEngine engine = new RuleEngine(List.of(r1), metrics);

        engine.evaluate(baseTransaction());

        // Verify the spy was called exactly once with the rule's name
        verify(metrics, times(1)).recordRuleTriggered("R1");
    }

    @Test
    @DisplayName("@Spy: recordRuleTriggered() NOT called when rule approves")
    void spyVerifiesNoCallWhenApproved() {
        Rule r1 = approveRule("APPROVE_RULE", 1);
        RuleEngine engine = new RuleEngine(List.of(r1), metrics);

        engine.evaluate(baseTransaction());

        // No rule triggered — metrics should not be called
        verify(metrics, never()).recordRuleTriggered(anyString());
    }

    @Test
    @DisplayName("@Spy: recordRuleTriggered() called for each non-approved rule before short-circuit")
    void spyVerifiesCallCountWithMultipleRules() {
        Rule r1 = reviewRule("REVIEW_RULE", 1);
        Rule r2 = declineRule("DECLINE_RULE", 2);
        Rule r3 = approveRule("NEVER_REACHED", 3);
        RuleEngine engine = new RuleEngine(List.of(r1, r2, r3), metrics);

        engine.evaluate(baseTransaction());

        // r1 triggers REVIEW (recordRuleTriggered called), r2 triggers DECLINE and short-circuits
        // r3 is never reached
        verify(metrics, times(1)).recordRuleTriggered("REVIEW_RULE");
        verify(metrics, times(1)).recordRuleTriggered("DECLINE_RULE");
        verify(metrics, never()).recordRuleTriggered("NEVER_REACHED");
    }

    // ── @ParameterizedTest: verdict precedence matrix ────────────────────────

    /**
     * Data provider for the verdict precedence and short-circuit matrix.
     * Each row describes a sequence of rule verdicts and the expected final outcome.
     *
     * Format: rule verdicts list (as string tag), expectedFinalVerdict, description
     */
    static Stream<Arguments> verdictPrecedenceMatrix() {
        return Stream.of(
                // All rules approve
                Arguments.of(List.of("APPROVE"),                          Verdict.APPROVED,      "single approve"),
                Arguments.of(List.of("APPROVE", "APPROVE"),              Verdict.APPROVED,      "all approve"),
                // REVIEW escalation
                Arguments.of(List.of("REVIEW"),                           Verdict.MANUAL_REVIEW, "single review"),
                Arguments.of(List.of("APPROVE", "REVIEW"),               Verdict.MANUAL_REVIEW, "approve then review"),
                Arguments.of(List.of("REVIEW", "APPROVE"),               Verdict.MANUAL_REVIEW, "review then approve"),
                Arguments.of(List.of("REVIEW", "REVIEW"),                Verdict.MANUAL_REVIEW, "two reviews"),
                // DECLINE short-circuit
                Arguments.of(List.of("DECLINE"),                          Verdict.DECLINED,      "immediate decline"),
                Arguments.of(List.of("REVIEW", "DECLINE"),               Verdict.DECLINED,      "review then decline"),
                Arguments.of(List.of("DECLINE", "APPROVE"),              Verdict.DECLINED,      "decline short-circuits approve"),
                // Empty rule set
                Arguments.of(List.of(),                                   Verdict.APPROVED,      "no rules = approve")
        );
    }

    @ParameterizedTest(name = "[{index}] rules={0} -> {1} ({2})")
    @MethodSource("verdictPrecedenceMatrix")
    @DisplayName("Verdict precedence and short-circuit matrix")
    void verdictPrecedenceMatrix(List<String> verdictTags, Verdict expected, String description) {
        List<Rule> rules = new java.util.ArrayList<>();
        for (int i = 0; i < verdictTags.size(); i++) {
            int order = i + 1;
            rules.add(switch (verdictTags.get(i)) {
                case "APPROVE"  -> approveRule("RULE_" + i, order);
                case "REVIEW"   -> reviewRule("RULE_" + i, order);
                case "DECLINE"  -> declineRule("RULE_" + i, order);
                default -> throw new IllegalArgumentException("Unknown: " + verdictTags.get(i));
            });
        }
        RuleEngine engine = new RuleEngine(rules, metrics);

        VerdictedTransaction result = engine.evaluate(baseTransaction());

        assertEquals(expected, result.getVerdict(), "Failed for: " + description);
        assertNotNull(result.getProcessedAt());
    }

    // ── Regular @Test: ordering, risk score, metadata ─────────────────────────

    @Test
    @DisplayName("Rules execute in ascending getOrder() sequence")
    void rulesExecuteInOrder() {
        // DECLINE at order 1 must fire before APPROVE at order 2
        Rule first  = declineRule("FIRST",  1);
        Rule second = approveRule("SECOND", 2);
        RuleEngine engine = new RuleEngine(List.of(second, first), metrics); // intentionally reversed

        VerdictedTransaction result = engine.evaluate(baseTransaction());

        assertEquals(Verdict.DECLINED, result.getVerdict());
        assertTrue(result.getReasons().getFirst().contains("FIRST"));
    }

    @Test
    @DisplayName("Total risk score is the sum of all evaluated rule risk scores")
    void riskScoreAccumulation() {
        // MANUAL_REVIEW = 50, then APPROVE = 0 → total = 50
        Rule r1 = reviewRule("R1", 1);
        Rule r2 = approveRule("R2", 2);
        RuleEngine engine = new RuleEngine(List.of(r1, r2), metrics);

        VerdictedTransaction result = engine.evaluate(baseTransaction());

        assertEquals(50, result.getTotalRiskScore());
    }

    @Test
    @DisplayName("Risk score stops accumulating after DECLINE short-circuit")
    void riskScoreStopsAtDecline() {
        // DECLINE (100) then REVIEW (50 would never be added)
        Rule r1 = declineRule("R1", 1);
        Rule r2 = reviewRule("R2", 2);
        RuleEngine engine = new RuleEngine(List.of(r1, r2), metrics);

        VerdictedTransaction result = engine.evaluate(baseTransaction());

        assertEquals(100, result.getTotalRiskScore(), "Only the DECLINE rule's score should be counted");
    }

    @Test
    @DisplayName("getRuleCount() returns the number of registered rules")
    void ruleCountReflectsRegisteredRules() {
        RuleEngine engine = new RuleEngine(List.of(approveRule("R1", 1), approveRule("R2", 2)), metrics);
        assertEquals(2, engine.getRuleCount());
    }
}
