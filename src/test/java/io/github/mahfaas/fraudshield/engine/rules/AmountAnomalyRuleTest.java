package io.github.mahfaas.fraudshield.engine.rules;

import io.github.mahfaas.fraudshield.engine.RuleResult;
import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AmountAnomalyRule}.
 *
 * <h3>Testing techniques demonstrated</h3>
 * <ul>
 *   <li>{@link ParameterizedTest} + {@link MethodSource} — data-driven verdict table
 *       covering all boundary conditions in one test method</li>
 *   <li>{@link ParameterizedTest} + {@link CsvSource} — inline CSV for concise
 *       runtime threshold-update scenarios</li>
 *   <li>Boundary-value analysis — exact thresholds, one-below, one-above</li>
 * </ul>
 */
class AmountAnomalyRuleTest {

    /** Default: review=100_000, decline=500_000 */
    private AmountAnomalyRule rule;

    @BeforeEach
    void setUp() {
        rule = new AmountAnomalyRule(
                BigDecimal.valueOf(500_000),
                BigDecimal.valueOf(100_000)
        );
    }

    private Transaction txWithAmount(long amount) {
        return Transaction.builder()
                .transactionId("tx-001")
                .accountId("ACC-001")
                .cardBin("411111")
                .amount(BigDecimal.valueOf(amount))
                .currency("RUB")
                .country("RU")
                .sourceIp("8.8.8.8")
                .timestamp(Instant.now())
                .build();
    }

    // ── @MethodSource: full verdict boundary table ───────────────────────────

    /**
     * Data provider for the verdict boundary matrix.
     * Covers below / at / above each threshold in one parameterized method.
     */
    static Stream<Arguments> verdictBoundaries() {
        return Stream.of(
                // amount, expectedVerdict, description
                Arguments.of(1L,          Verdict.APPROVED,      "well below review threshold"),
                Arguments.of(99_999L,     Verdict.APPROVED,      "one below review threshold"),
                Arguments.of(100_000L,    Verdict.APPROVED,      "exactly at review threshold (inclusive APPROVE)"),
                Arguments.of(100_001L,    Verdict.MANUAL_REVIEW, "one above review threshold"),
                Arguments.of(250_000L,    Verdict.MANUAL_REVIEW, "midway between thresholds"),
                Arguments.of(499_999L,    Verdict.MANUAL_REVIEW, "one below decline threshold"),
                Arguments.of(500_000L,    Verdict.MANUAL_REVIEW, "exactly at decline threshold (inclusive REVIEW)"),
                Arguments.of(500_001L,    Verdict.DECLINED,      "one above decline threshold"),
                Arguments.of(9_999_999L,  Verdict.DECLINED,      "very large amount")
        );
    }

    @ParameterizedTest(name = "[{index}] amount={0} -> {1} ({2})")
    @MethodSource("verdictBoundaries")
    @DisplayName("Verdict boundary matrix - covers all threshold edge cases")
    void verdictBoundaryMatrix(long amount, Verdict expected, String description) {
        RuleResult result = rule.evaluate(txWithAmount(amount));

        assertEquals(expected, result.getVerdict(),
                "Failed for: " + description + " (amount=" + amount + ")");

        // Risk score must match verdict semantics
        if (expected == Verdict.DECLINED)      assertEquals(100, result.getRiskScore());
        if (expected == Verdict.MANUAL_REVIEW) assertEquals(50,  result.getRiskScore());
        if (expected == Verdict.APPROVED)      assertEquals(0,   result.getRiskScore());
    }

    // ── @CsvSource: runtime threshold update ────────────────────────────────

    /**
     * Tests that updating thresholds at runtime immediately changes verdicts.
     * Demonstrates {@link CsvSource} for concise inline data tables.
     *
     * Columns: newDecline, newReview, testAmount, expectedVerdict
     */
    @ParameterizedTest(name = "[{index}] decline={0} review={1} amount={2} -> {3}")
    @CsvSource({
            "200000, 50000,  60000, MANUAL_REVIEW",
            "200000, 50000, 250000, DECLINED",
            "200000, 50000,  40000, APPROVED",
            "100000, 10000,  50000, MANUAL_REVIEW",
            "100000, 10000, 150000, DECLINED"
    })
    @DisplayName("Runtime threshold updates are reflected immediately")
    void runtimeThresholdUpdate(long newDecline, long newReview, long amount, Verdict expected) {
        rule.setDeclineThreshold(BigDecimal.valueOf(newDecline));
        rule.setReviewThreshold(BigDecimal.valueOf(newReview));

        RuleResult result = rule.evaluate(txWithAmount(amount));
        assertEquals(expected, result.getVerdict());
    }

    // ── Regular @Test: rule metadata ─────────────────────────────────────────

    @Test
    @DisplayName("Rule name is AMOUNT_ANOMALY")
    void ruleName() {
        assertEquals("AMOUNT_ANOMALY", rule.getName());
    }

    @Test
    @DisplayName("Rule order is 20 (after Blacklist=10, before Velocity=30)")
    void ruleOrder() {
        assertEquals(20, rule.getOrder());
    }

    @Test
    @DisplayName("Declined result carries a non-null, non-blank reason message")
    void declineHasReason() {
        RuleResult result = rule.evaluate(txWithAmount(600_000));
        assertEquals(Verdict.DECLINED, result.getVerdict());
        assertNotNull(result.getReason(), "DECLINED result must include a reason");
        assertFalse(result.getReason().isBlank());
    }
}
