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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VelocityRuleTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private VelocityRule rule;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        rule = new VelocityRule(redisTemplate, 5, 60);
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
    @DisplayName("Should APPROVE when transaction count is within limit")
    void shouldApproveWithinLimit() {
        when(valueOps.increment("velocity:ACC-001")).thenReturn(3L);

        RuleResult result = rule.evaluate(baseTransaction().build());

        assertEquals(Verdict.APPROVED, result.getVerdict());
    }

    @Test
    @DisplayName("Should DECLINE when transaction count exceeds limit")
    void shouldDeclineWhenExceedsLimit() {
        when(valueOps.increment("velocity:ACC-001")).thenReturn(6L);

        RuleResult result = rule.evaluate(baseTransaction().build());

        assertEquals(Verdict.DECLINED, result.getVerdict());
        assertEquals("VELOCITY", result.getRuleName());
    }

    @Test
    @DisplayName("Should set TTL on first transaction (count == 1)")
    void shouldSetTtlOnFirstTransaction() {
        when(valueOps.increment("velocity:ACC-001")).thenReturn(1L);

        rule.evaluate(baseTransaction().build());

        verify(redisTemplate).expire(eq("velocity:ACC-001"), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("Should NOT set TTL on subsequent transactions (count > 1)")
    void shouldNotSetTtlOnSubsequentTransactions() {
        when(valueOps.increment("velocity:ACC-001")).thenReturn(3L);

        rule.evaluate(baseTransaction().build());

        verify(redisTemplate, never()).expire(any(), any(Duration.class));
    }

    @Test
    @DisplayName("Should track per-account, not globally")
    void shouldTrackPerAccount() {
        when(valueOps.increment("velocity:ACC-001")).thenReturn(1L);
        when(valueOps.increment("velocity:ACC-002")).thenReturn(1L);

        rule.evaluate(baseTransaction().accountId("ACC-001").build());
        rule.evaluate(baseTransaction().accountId("ACC-002").build());

        verify(valueOps).increment("velocity:ACC-001");
        verify(valueOps).increment("velocity:ACC-002");
    }

    @Test
    @DisplayName("Should APPROVE at exactly the limit (not strictly greater)")
    void shouldApproveAtExactLimit() {
        when(valueOps.increment("velocity:ACC-001")).thenReturn(5L);

        RuleResult result = rule.evaluate(baseTransaction().build());

        assertEquals(Verdict.APPROVED, result.getVerdict());
    }

    @Test
    @DisplayName("Should allow updating thresholds at runtime")
    void shouldUpdateThresholds() {
        rule.setMaxTransactions(2);
        when(valueOps.increment("velocity:ACC-001")).thenReturn(3L);

        RuleResult result = rule.evaluate(baseTransaction().build());

        assertEquals(Verdict.DECLINED, result.getVerdict());
    }
}
