package io.github.mahfaas.fraudshield.engine.rules;

import io.github.mahfaas.fraudshield.engine.RuleResult;
import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VelocityRule}.
 *
 * <h3>Testing techniques demonstrated</h3>
 * <ul>
 *   <li>{@link ParameterizedTest} + {@link MethodSource} — verdict boundary matrix
 *       crossing the max-transactions threshold from multiple directions</li>
 *   <li>{@link ParameterizedTest} + {@link CsvSource} — multi-config table: different
 *       maxTransactions limits paired with different redis counts</li>
 *   <li>Mockito {@link Mock} for Redis — isolates the rule from infrastructure</li>
 *   <li>Mockito {@code verify} — asserts TTL is set only on first transaction</li>
 * </ul>
 */
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
        rule = new VelocityRule(redisTemplate, 5, 60L);
    }

    private Transaction txForAccount(String accountId) {
        return Transaction.builder()
                .transactionId("tx-001")
                .accountId(accountId)
                .cardBin("411111")
                .amount(BigDecimal.valueOf(1000))
                .currency("RUB")
                .country("RU")
                .sourceIp("8.8.8.8")
                .timestamp(Instant.now())
                .build();
    }

    // ── @MethodSource: transaction count boundary matrix ────────────────────

    /**
     * Data provider: redis count vs expected verdict at max=5.
     * Tests boundary values around the configured max-transactions limit.
     */
    static Stream<Arguments> countVerdictBoundaries() {
        return Stream.of(
                // redisCount, expectedVerdict, label
                Arguments.of(1L,  Verdict.APPROVED, "first transaction"),
                Arguments.of(2L,  Verdict.APPROVED, "well below limit"),
                Arguments.of(4L,  Verdict.APPROVED, "one below limit"),
                Arguments.of(5L,  Verdict.APPROVED, "exactly at limit (inclusive APPROVE)"),
                Arguments.of(6L,  Verdict.DECLINED, "one over limit"),
                Arguments.of(10L, Verdict.DECLINED, "well over limit"),
                Arguments.of(100L,Verdict.DECLINED, "extreme over-limit")
        );
    }

    @ParameterizedTest(name = "[{index}] count={0} -> {1} ({2})")
    @MethodSource("countVerdictBoundaries")
    @DisplayName("Verdict boundary matrix at maxTransactions=5")
    void velocityBoundaryMatrix(long count, Verdict expected, String label) {
        when(valueOps.increment("velocity:ACC-001")).thenReturn(count);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        RuleResult result = rule.evaluate(txForAccount("ACC-001"));

        assertEquals(expected, result.getVerdict(), "Failed for: " + label);
        if (expected == Verdict.DECLINED) {
            assertNotNull(result.getReason());
            assertFalse(result.getReason().isBlank());
        }
    }

    // ── @CsvSource: different max-limits vs different counts ────────────────

    /**
     * Tests that the rule respects dynamically updated maxTransactions limits.
     * Columns: newMax, redisCount, expectedVerdict
     */
    @ParameterizedTest(name = "[{index}] maxTx={0} count={1} -> {2}")
    @CsvSource({
            "3, 2, APPROVED",     // count below new limit
            "3, 3, APPROVED",     // count at new limit
            "3, 4, DECLINED",     // count above new limit
            "10, 9, APPROVED",    // very permissive limit
            "10, 11, DECLINED",   // slightly over permissive limit
            "1,  1, APPROVED",    // strictest: exactly 1 allowed
            "1,  2, DECLINED"     // strictest: 2 triggers decline
    })
    @DisplayName("Dynamic maxTransactions updates are respected")
    void dynamicLimitUpdate(int newMax, long count, Verdict expected) {
        rule.setMaxTransactions(newMax);
        when(valueOps.increment("velocity:ACC-001")).thenReturn(count);

        RuleResult result = rule.evaluate(txForAccount("ACC-001"));
        assertEquals(expected, result.getVerdict());
    }

    // ── Regular @Test: TTL and per-account tracking ──────────────────────────

    @Test
    @DisplayName("TTL is set on the first transaction (count==1)")
    void ttlSetOnFirstTransaction() {
        when(valueOps.increment("velocity:ACC-001")).thenReturn(1L);

        rule.evaluate(txForAccount("ACC-001"));

        verify(redisTemplate).expire(eq("velocity:ACC-001"), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("TTL is NOT set on subsequent transactions (count>1)")
    void ttlNotSetOnSubsequentTransactions() {
        when(valueOps.increment("velocity:ACC-001")).thenReturn(3L);

        rule.evaluate(txForAccount("ACC-001"));

        verify(redisTemplate, never()).expire(any(), any(Duration.class));
    }

    @Test
    @DisplayName("Counters are per-account, not shared globally")
    void countersArePerAccount() {
        when(valueOps.increment("velocity:ACC-001")).thenReturn(1L);
        when(valueOps.increment("velocity:ACC-002")).thenReturn(1L);

        rule.evaluate(txForAccount("ACC-001"));
        rule.evaluate(txForAccount("ACC-002"));

        verify(valueOps).increment("velocity:ACC-001");
        verify(valueOps).increment("velocity:ACC-002");
    }

    @Test
    @DisplayName("Rule name is VELOCITY")
    void ruleName() {
        assertEquals("VELOCITY", rule.getName());
    }

    @Test
    @DisplayName("Rule order is 30 (after BLACKLIST=10 and AMOUNT_ANOMALY=20)")
    void ruleOrder() {
        assertEquals(30, rule.getOrder());
    }
}
