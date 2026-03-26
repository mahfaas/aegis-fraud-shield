package io.github.mahfaas.fraudshield.engine;

import io.github.mahfaas.fraudshield.metrics.FraudMetrics;
import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.Verdict;
import io.github.mahfaas.fraudshield.model.VerdictedTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RuleEngineTest {

    @Mock
    private FraudMetrics metrics;

    private Transaction.TransactionBuilder baseTransaction() {
        return Transaction.builder()
                .transactionId("tx-001")
                .accountId("ACC-001")
                .cardBin("411111")
                .amount(BigDecimal.valueOf(1000))
                .currency("RUB")
                .country("RU")
                .sourceIp("8.8.8.8")
                .timestamp(Instant.now());
    }

    @Test
    @DisplayName("Should APPROVE when all rules pass")
    void shouldApproveWhenAllRulesPass() {
        Rule alwaysApprove = new Rule() {
            @Override public RuleResult evaluate(Transaction tx) { return RuleResult.approve("TEST"); }
            @Override public String getName() { return "TEST"; }
        };
        RuleEngine engine = new RuleEngine(List.of(alwaysApprove), metrics);

        VerdictedTransaction result = engine.evaluate(baseTransaction().build());

        assertEquals(Verdict.APPROVED, result.getVerdict());
        assertTrue(result.getReasons().isEmpty());
        assertNotNull(result.getProcessedAt());
        assertEquals(0, result.getTotalRiskScore());
    }

    @Test
    @DisplayName("Should DECLINE and short-circuit on first DECLINE")
    void shouldDeclineAndShortCircuit() {
        Rule declineRule = new Rule() {
            @Override public RuleResult evaluate(Transaction tx) { return RuleResult.decline("DECLINE_RULE", "blocked"); }
            @Override public String getName() { return "DECLINE_RULE"; }
            @Override public int getOrder() { return 1; }
        };
        Rule neverReachedRule = new Rule() {
            @Override public RuleResult evaluate(Transaction tx) { fail("Should not be reached"); return null; }
            @Override public String getName() { return "NEVER_REACHED"; }
            @Override public int getOrder() { return 2; }
        };
        RuleEngine engine = new RuleEngine(List.of(neverReachedRule, declineRule), metrics);

        VerdictedTransaction result = engine.evaluate(baseTransaction().build());

        assertEquals(Verdict.DECLINED, result.getVerdict());
        assertEquals(1, result.getReasons().size());
        assertEquals(100, result.getTotalRiskScore());
    }

    @Test
    @DisplayName("Should return MANUAL_REVIEW when a rule flags but none decline")
    void shouldReturnManualReview() {
        Rule reviewRule = new Rule() {
            @Override public RuleResult evaluate(Transaction tx) { return RuleResult.manualReview("REVIEW_RULE", "suspicious"); }
            @Override public String getName() { return "REVIEW_RULE"; }
            @Override public int getOrder() { return 1; }
        };
        Rule approveRule = new Rule() {
            @Override public RuleResult evaluate(Transaction tx) { return RuleResult.approve("OK_RULE"); }
            @Override public String getName() { return "OK_RULE"; }
            @Override public int getOrder() { return 2; }
        };
        RuleEngine engine = new RuleEngine(List.of(reviewRule, approveRule), metrics);

        VerdictedTransaction result = engine.evaluate(baseTransaction().build());

        assertEquals(Verdict.MANUAL_REVIEW, result.getVerdict());
        assertEquals(1, result.getReasons().size());
        assertEquals(50, result.getTotalRiskScore());
    }

    @Test
    @DisplayName("Should execute rules in order by getOrder()")
    void shouldRespectOrder() {
        Rule firstRule = new Rule() {
            @Override public RuleResult evaluate(Transaction tx) { return RuleResult.decline("FIRST", "first blocked"); }
            @Override public String getName() { return "FIRST"; }
            @Override public int getOrder() { return 1; }
        };
        Rule secondRule = new Rule() {
            @Override public RuleResult evaluate(Transaction tx) { fail("Should not be reached"); return null; }
            @Override public String getName() { return "SECOND"; }
            @Override public int getOrder() { return 2; }
        };

        RuleEngine engine = new RuleEngine(List.of(secondRule, firstRule), metrics);

        VerdictedTransaction result = engine.evaluate(baseTransaction().build());

        assertEquals(Verdict.DECLINED, result.getVerdict());
        assertTrue(result.getReasons().getFirst().contains("FIRST"));
    }

    @Test
    @DisplayName("Should handle empty rule list")
    void shouldHandleEmptyRules() {
        RuleEngine engine = new RuleEngine(List.of(), metrics);
        VerdictedTransaction result = engine.evaluate(baseTransaction().build());
        assertEquals(Verdict.APPROVED, result.getVerdict());
        assertEquals(0, result.getTotalRiskScore());
    }
}
