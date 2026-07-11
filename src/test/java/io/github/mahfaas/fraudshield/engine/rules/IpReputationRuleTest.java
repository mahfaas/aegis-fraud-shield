package io.github.mahfaas.fraudshield.engine.rules;

import io.github.mahfaas.fraudshield.engine.RuleResult;
import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IpReputationRuleTest {

    @Mock
    private IpReputationRepository repository;

    private IpReputationRule rule;

    @BeforeEach
    void setUp() {
        rule = new IpReputationRule(repository);
        rule.load(List.of(
                IpReputationEntity.builder().ipPrefix("185.220.").country("DE").category("TOR_EXIT").riskScore(100).build(),
                IpReputationEntity.builder().ipPrefix("45.83.").country("NL").category("DATACENTER").riskScore(50).build(),
                IpReputationEntity.builder().ipPrefix("45.83.111.").country("US").category("KNOWN_MERCHANT_GATEWAY").riskScore(0).build()
        ));
    }

    private Transaction.TransactionBuilder baseTransaction() {
        return Transaction.builder()
                .transactionId("tx-001")
                .accountId("ACC-001")
                .cardBin("411111")
                .amount(BigDecimal.valueOf(1000))
                .currency("USD")
                .country("US")
                .timestamp(Instant.now());
    }

    @Test
    @DisplayName("Should DECLINE transaction from a risk-score-100 IP range")
    void shouldDeclineHighRiskRange() {
        Transaction tx = baseTransaction().sourceIp("185.220.1.5").country("DE").build();

        RuleResult result = rule.evaluate(tx);

        assertEquals(Verdict.DECLINED, result.getVerdict());
        assertEquals("IP_REPUTATION", result.getRuleName());
        assertTrue(result.getReason().contains("TOR_EXIT"));
    }

    @Test
    @DisplayName("Should flag MANUAL_REVIEW for a risk-score-50 range even with matching country")
    void shouldFlagRiskyRangeSameCountry() {
        Transaction tx = baseTransaction().sourceIp("45.83.9.1").country("NL").build();

        RuleResult result = rule.evaluate(tx);

        assertEquals(Verdict.MANUAL_REVIEW, result.getVerdict());
        assertTrue(result.getReason().contains("DATACENTER"));
    }

    @Test
    @DisplayName("Should flag MANUAL_REVIEW on geo-mismatch even for a zero-risk-score range")
    void shouldFlagGeoMismatchForZeroRiskRange() {
        // "45.83.111." is a more specific match than "45.83." and carries riskScore=0,
        // but the transaction's declared country (US) doesn't match the entry's country (US)... use RU to mismatch.
        Transaction tx = baseTransaction().sourceIp("45.83.111.7").country("RU").build();

        RuleResult result = rule.evaluate(tx);

        assertEquals(Verdict.MANUAL_REVIEW, result.getVerdict());
        assertTrue(result.getReason().contains("geolocates to US"));
        assertTrue(result.getReason().contains("declares RU"));
    }

    @Test
    @DisplayName("Should APPROVE when the longest-prefix match's country and risk score are both clean")
    void shouldApproveCleanMatch() {
        Transaction tx = baseTransaction().sourceIp("45.83.111.7").country("US").build();

        RuleResult result = rule.evaluate(tx);

        assertEquals(Verdict.APPROVED, result.getVerdict());
    }

    @Test
    @DisplayName("Should prefer the longest (most specific) matching prefix")
    void shouldPreferLongestPrefixMatch() {
        // "45.83.111.7" matches both "45.83." (riskScore=50) and "45.83.111." (riskScore=0, same country) —
        // the more specific "45.83.111." entry must win, so this approves.
        Transaction tx = baseTransaction().sourceIp("45.83.111.7").country("US").build();

        RuleResult result = rule.evaluate(tx);

        assertEquals(Verdict.APPROVED, result.getVerdict());
    }

    @Test
    @DisplayName("Should APPROVE when no prefix matches")
    void shouldApproveNoMatch() {
        Transaction tx = baseTransaction().sourceIp("8.8.8.8").build();

        RuleResult result = rule.evaluate(tx);

        assertEquals(Verdict.APPROVED, result.getVerdict());
    }

    @Test
    @DisplayName("Should handle null/blank source IP gracefully")
    void shouldHandleMissingSourceIp() {
        assertEquals(Verdict.APPROVED, rule.evaluate(baseTransaction().sourceIp(null).build()).getVerdict());
        assertEquals(Verdict.APPROVED, rule.evaluate(baseTransaction().sourceIp("").build()).getVerdict());
    }

    @Test
    @DisplayName("init() loads entries from the repository")
    void initLoadsFromRepository() {
        IpReputationRule freshRule = new IpReputationRule(repository);
        org.mockito.Mockito.when(repository.findAll()).thenReturn(List.of(
                IpReputationEntity.builder().ipPrefix("1.2.3.").country("US").category("TOR_EXIT").riskScore(100).build()
        ));

        freshRule.init();
        RuleResult result = freshRule.evaluate(baseTransaction().sourceIp("1.2.3.4").country("US").build());

        assertEquals(Verdict.DECLINED, result.getVerdict());
        verify(repository).findAll();
    }
}
