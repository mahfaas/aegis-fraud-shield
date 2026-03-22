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

class BlacklistRuleTest {

    private BlacklistRule rule;

    @BeforeEach
    void setUp() {
        rule = new BlacklistRule();
        rule.addIp("10.0.0.1");
        rule.addIp("192.168.1.100");
        rule.addBin("427600");
        rule.addBin("531234");
    }

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
    @DisplayName("Should DECLINE transaction with blacklisted IP")
    void shouldDeclineBlacklistedIp() {
        Transaction tx = baseTransaction().sourceIp("10.0.0.1").build();
        RuleResult result = rule.evaluate(tx);
        assertEquals(Verdict.DECLINED, result.getVerdict());
        assertEquals("BLACKLIST", result.getRuleName());
    }

    @Test
    @DisplayName("Should DECLINE transaction with blacklisted BIN")
    void shouldDeclineBlacklistedBin() {
        Transaction tx = baseTransaction().cardBin("427600").build();
        RuleResult result = rule.evaluate(tx);
        assertEquals(Verdict.DECLINED, result.getVerdict());
    }

    @Test
    @DisplayName("Should APPROVE transaction with clean IP and BIN")
    void shouldApproveCleanTransaction() {
        Transaction tx = baseTransaction().build();
        RuleResult result = rule.evaluate(tx);
        assertEquals(Verdict.APPROVED, result.getVerdict());
    }

    @Test
    @DisplayName("Should handle null IP gracefully")
    void shouldHandleNullIp() {
        Transaction tx = baseTransaction().sourceIp(null).build();
        RuleResult result = rule.evaluate(tx);
        assertEquals(Verdict.APPROVED, result.getVerdict());
    }

    @Test
    @DisplayName("Should remove IP from blacklist")
    void shouldRemoveIp() {
        rule.removeIp("10.0.0.1");
        Transaction tx = baseTransaction().sourceIp("10.0.0.1").build();
        RuleResult result = rule.evaluate(tx);
        assertEquals(Verdict.APPROVED, result.getVerdict());
    }

    @Test
    @DisplayName("Should remove BIN from blacklist")
    void shouldRemoveBin() {
        rule.removeBin("427600");
        Transaction tx = baseTransaction().cardBin("427600").build();
        RuleResult result = rule.evaluate(tx);
        assertEquals(Verdict.APPROVED, result.getVerdict());
    }
}
