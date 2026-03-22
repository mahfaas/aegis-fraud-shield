package io.github.mahfaas.fraudshield.engine.rules;

import io.github.mahfaas.fraudshield.engine.RuleResult;
import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AmountAnomalyRuleTest {

    private AmountAnomalyRule rule;

    @BeforeEach
    void setUp() {
        rule = new AmountAnomalyRule(
                BigDecimal.valueOf(500_000),  // decline threshold
                BigDecimal.valueOf(100_000)   // review threshold
        );
    }

    private Transaction.TransactionBuilder baseTransaction() {
        return Transaction.builder()
                .transactionId("tx-001")
                .accountId("ACC-001")
                .cardBin("411111")
                .currency("RUB")
                .country("RU")
                .sourceIp("8.8.8.8")
                .timestamp(Instant.now());
    }

    @Test
    @DisplayName("Should APPROVE transaction below review threshold")
    void shouldApproveNormalAmount() {
        Transaction tx = baseTransaction().amount(BigDecimal.valueOf(5000)).build();
        RuleResult result = rule.evaluate(tx);
        assertEquals(Verdict.APPROVED, result.getVerdict());
    }

    @Test
    @DisplayName("Should flag MANUAL_REVIEW for amount above review but below decline threshold")
    void shouldFlagManualReview() {
        Transaction tx = baseTransaction().amount(BigDecimal.valueOf(200_000)).build();
        RuleResult result = rule.evaluate(tx);
        assertEquals(Verdict.MANUAL_REVIEW, result.getVerdict());
    }

    @Test
    @DisplayName("Should DECLINE transaction above decline threshold")
    void shouldDeclineHighAmount() {
        Transaction tx = baseTransaction().amount(BigDecimal.valueOf(1_000_000)).build();
        RuleResult result = rule.evaluate(tx);
        assertEquals(Verdict.DECLINED, result.getVerdict());
    }

    @Test
    @DisplayName("Should APPROVE amount exactly at review threshold")
    void shouldApproveAtReviewThreshold() {
        Transaction tx = baseTransaction().amount(BigDecimal.valueOf(100_000)).build();
        RuleResult result = rule.evaluate(tx);
        assertEquals(Verdict.APPROVED, result.getVerdict());
    }

    @Test
    @DisplayName("Should update thresholds at runtime")
    void shouldUpdateThresholds() {
        rule.setDeclineThreshold(BigDecimal.valueOf(200_000));
        rule.setReviewThreshold(BigDecimal.valueOf(50_000));

        Transaction tx = baseTransaction().amount(BigDecimal.valueOf(60_000)).build();
        RuleResult result = rule.evaluate(tx);
        assertEquals(Verdict.MANUAL_REVIEW, result.getVerdict());

        Transaction tx2 = baseTransaction().amount(BigDecimal.valueOf(250_000)).build();
        RuleResult result2 = rule.evaluate(tx2);
        assertEquals(Verdict.DECLINED, result2.getVerdict());
    }
}
