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
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeoVelocityRuleTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private GeoVelocityRule rule;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        rule = new GeoVelocityRule(redisTemplate);
    }

    private Transaction.TransactionBuilder baseTransaction() {
        return Transaction.builder()
                .transactionId("tx-001")
                .accountId("ACC-001")
                .cardBin("411111")
                .amount(BigDecimal.valueOf(1000))
                .currency("RUB")
                .sourceIp("8.8.8.8")
                .timestamp(Instant.now());
    }

    @Test
    @DisplayName("Should APPROVE first transaction from any country")
    void shouldApproveFirstTransaction() {
        when(valueOps.get("geo:ACC-001")).thenReturn(null);

        RuleResult result = rule.evaluate(baseTransaction().country("RU").build());

        assertEquals(Verdict.APPROVED, result.getVerdict());
    }

    @Test
    @DisplayName("Should APPROVE same country transactions")
    void shouldApproveSameCountry() {
        long now = Instant.now().toEpochMilli();
        when(valueOps.get("geo:ACC-001")).thenReturn("RU|" + now);

        RuleResult result = rule.evaluate(baseTransaction().country("RU").timestamp(Instant.ofEpochMilli(now + 5000)).build());

        assertEquals(Verdict.APPROVED, result.getVerdict());
    }

    @Test
    @DisplayName("Should flag MANUAL_REVIEW for different country within window")
    void shouldFlagImpossibleTravel() {
        long now = Instant.now().toEpochMilli();
        when(valueOps.get("geo:ACC-001")).thenReturn("RU|" + now);

        Transaction tx = baseTransaction()
                .country("US")
                .timestamp(Instant.ofEpochMilli(now + 1800_000)) // 30 min later
                .build();

        RuleResult result = rule.evaluate(tx);

        assertEquals(Verdict.MANUAL_REVIEW, result.getVerdict());
        assertTrue(result.getReason().contains("Impossible travel"));
        assertTrue(result.getReason().contains("RU"));
        assertTrue(result.getReason().contains("US"));
    }

    @Test
    @DisplayName("Should APPROVE different country after window expires")
    void shouldApproveAfterWindowExpires() {
        long pastTime = Instant.now().toEpochMilli() - 7200_000; // 2 hours ago
        when(valueOps.get("geo:ACC-001")).thenReturn("RU|" + pastTime);

        Transaction tx = baseTransaction()
                .country("US")
                .timestamp(Instant.now())
                .build();

        RuleResult result = rule.evaluate(tx);

        assertEquals(Verdict.APPROVED, result.getVerdict());
    }

    @Test
    @DisplayName("Should store country and timestamp in Redis with TTL")
    void shouldStoreInRedis() {
        when(valueOps.get("geo:ACC-001")).thenReturn(null);
        Instant ts = Instant.now();

        rule.evaluate(baseTransaction().country("RU").timestamp(ts).build());

        verify(valueOps).set(eq("geo:ACC-001"), eq("RU|" + ts.toEpochMilli()), eq(Duration.ofSeconds(3600)));
    }
}
