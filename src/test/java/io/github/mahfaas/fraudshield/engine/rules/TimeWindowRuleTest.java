package io.github.mahfaas.fraudshield.engine.rules;

import io.github.mahfaas.fraudshield.config.TimeWindowRuleConfig;
import io.github.mahfaas.fraudshield.engine.RuleResult;
import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TimeWindowRule}.
 *
 * <h3>Testing strategy</h3>
 * <ul>
 *   <li>No Spring context — the rule and its config are constructed directly.</li>
 *   <li>{@link Nested} test classes group scenarios by detection path
 *       (safe category / night window / weekend / midnight-wrapping window).</li>
 *   <li>Timestamps are crafted with explicit UTC epoch offsets to remove
 *       any dependency on the test-runner's local timezone.</li>
 * </ul>
 *
 * <h3>Key UTC reference points used in this test</h3>
 * <pre>
 *   2026-06-08T03:00:00Z  → Monday   03:00 UTC (inside 02–05 night window)
 *   2026-06-08T01:00:00Z  → Monday   01:00 UTC (before night window)
 *   2026-06-08T10:00:00Z  → Monday   10:00 UTC (business hours, safe)
 *   2026-06-13T10:00:00Z  → Saturday 10:00 UTC (weekend, business hours)
 *   2026-06-14T10:00:00Z  → Sunday   10:00 UTC (weekend, business hours)
 *   2026-06-09T10:00:00Z  → Tuesday  10:00 UTC (weekday, business hours)
 * </pre>
 */
class TimeWindowRuleTest {

    // Fixed UTC instants used across tests
    /** Monday 03:00 UTC — inside default night window [02:00–05:00) */
    private static final Instant MON_3AM_UTC = Instant.parse("2026-06-08T03:00:00Z");
    /** Monday 01:00 UTC — before night window */
    private static final Instant MON_1AM_UTC = Instant.parse("2026-06-08T01:00:00Z");
    /** Monday 10:00 UTC — safe business hours */
    private static final Instant MON_10AM_UTC = Instant.parse("2026-06-08T10:00:00Z");
    /** Monday 02:00 UTC — exactly at start of window (inclusive) */
    private static final Instant MON_2AM_UTC = Instant.parse("2026-06-08T02:00:00Z");
    /** Monday 05:00 UTC — exactly at end of window (exclusive → safe) */
    private static final Instant MON_5AM_UTC = Instant.parse("2026-06-08T05:00:00Z");
    /** Saturday 10:00 UTC */
    private static final Instant SAT_10AM_UTC = Instant.parse("2026-06-13T10:00:00Z");
    /** Sunday 10:00 UTC */
    private static final Instant SUN_10AM_UTC = Instant.parse("2026-06-14T10:00:00Z");
    /** Tuesday 10:00 UTC */
    private static final Instant TUE_10AM_UTC = Instant.parse("2026-06-09T10:00:00Z");

    private TimeWindowRuleConfig config;
    private TimeWindowRule rule;

    @BeforeEach
    void setUp() {
        config = new TimeWindowRuleConfig();
        // Defaults: start=2, end=5, flagWeekends=true, categories=[gambling, crypto, ...]
        rule = new TimeWindowRule(config);
    }

    private Transaction txWith(String category, Instant timestamp) {
        return Transaction.builder()
                .transactionId("tx-tw-001")
                .accountId("ACC-TW")
                .cardBin("411111")
                .amount(BigDecimal.valueOf(500))
                .currency("USD")
                .country("US")
                .merchantCategory(category)
                .sourceIp("10.0.0.1")
                .timestamp(timestamp)
                .build();
    }

    // ── Safe categories always pass ───────────────────────────────────────────

    @Nested
    @DisplayName("Safe merchant categories are never flagged")
    class SafeCategories {

        @ParameterizedTest(name = "category={0} at night → APPROVED")
        @ValueSource(strings = {"electronics", "groceries", "restaurant", "retail", "healthcare", "UNKNOWN"})
        @DisplayName("Non-high-risk categories pass even during night window")
        void safeCategoryAtNightPasses(String category) {
            RuleResult result = rule.evaluate(txWith(category, MON_3AM_UTC));
            assertEquals(Verdict.APPROVED, result.getVerdict());
            assertEquals(0, result.getRiskScore());
        }

        @Test
        @DisplayName("Null merchant category is treated as safe")
        void nullCategoryPasses() {
            RuleResult result = rule.evaluate(txWith(null, MON_3AM_UTC));
            assertEquals(Verdict.APPROVED, result.getVerdict());
        }
    }

    // ── Night window detection ────────────────────────────────────────────────

    @Nested
    @DisplayName("Night window detection [02:00–05:00 UTC)")
    class NightWindow {

        @Test
        @DisplayName("High-risk category at 03:00 UTC → MANUAL_REVIEW")
        void highRiskCategoryAt3amFlagged() {
            RuleResult result = rule.evaluate(txWith("gambling", MON_3AM_UTC));
            assertEquals(Verdict.MANUAL_REVIEW, result.getVerdict());
            assertEquals(50, result.getRiskScore());
            assertNotNull(result.getReason());
            assertTrue(result.getReason().contains("gambling"));
        }

        @Test
        @DisplayName("High-risk category exactly at window start 02:00 UTC → MANUAL_REVIEW (inclusive)")
        void exactlyAtWindowStart() {
            RuleResult result = rule.evaluate(txWith("crypto", MON_2AM_UTC));
            assertEquals(Verdict.MANUAL_REVIEW, result.getVerdict());
        }

        @Test
        @DisplayName("High-risk category exactly at window end 05:00 UTC → APPROVED (exclusive)")
        void exactlyAtWindowEnd() {
            RuleResult result = rule.evaluate(txWith("crypto", MON_5AM_UTC));
            assertEquals(Verdict.APPROVED, result.getVerdict());
        }

        @Test
        @DisplayName("High-risk category at 01:00 UTC (before window) → APPROVED")
        void highRiskCategoryBefore2amPasses() {
            RuleResult result = rule.evaluate(txWith("wire-transfer", MON_1AM_UTC));
            // 01:00 is before 02:00 start; weekday → APPROVED
            assertEquals(Verdict.APPROVED, result.getVerdict());
        }

        @Test
        @DisplayName("High-risk category at 10:00 UTC business hours → APPROVED")
        void highRiskCategoryAtBusinessHoursPasses() {
            RuleResult result = rule.evaluate(txWith("forex", MON_10AM_UTC));
            assertEquals(Verdict.APPROVED, result.getVerdict());
        }
    }

    // ── Weekend detection ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Weekend detection")
    class WeekendDetection {

        @Test
        @DisplayName("High-risk category on Saturday at 10:00 UTC → MANUAL_REVIEW")
        void saturdayFlagged() {
            RuleResult result = rule.evaluate(txWith("crypto", SAT_10AM_UTC));
            assertEquals(Verdict.MANUAL_REVIEW, result.getVerdict());
            assertTrue(result.getReason().contains("SATURDAY"));
        }

        @Test
        @DisplayName("High-risk category on Sunday at 10:00 UTC → MANUAL_REVIEW")
        void sundayFlagged() {
            RuleResult result = rule.evaluate(txWith("gambling", SUN_10AM_UTC));
            assertEquals(Verdict.MANUAL_REVIEW, result.getVerdict());
            assertTrue(result.getReason().contains("SUNDAY"));
        }

        @Test
        @DisplayName("High-risk category on Tuesday at 10:00 UTC → APPROVED")
        void weekdayNotFlagged() {
            RuleResult result = rule.evaluate(txWith("crypto", TUE_10AM_UTC));
            assertEquals(Verdict.APPROVED, result.getVerdict());
        }

        @Test
        @DisplayName("Weekend flagging disabled → Saturday transaction passes")
        void weekendFlaggingDisabled() {
            config.setFlagWeekends(false);
            RuleResult result = rule.evaluate(txWith("crypto", SAT_10AM_UTC));
            assertEquals(Verdict.APPROVED, result.getVerdict());
        }

        @Test
        @DisplayName("Safe category on Saturday → APPROVED")
        void safeCategoryOnWeekendPasses() {
            RuleResult result = rule.evaluate(txWith("groceries", SAT_10AM_UTC));
            assertEquals(Verdict.APPROVED, result.getVerdict());
        }
    }

    // ── Midnight-wrapping window ──────────────────────────────────────────────

    @Nested
    @DisplayName("Midnight-wrapping window (e.g. 22:00–04:00)")
    class MidnightWrappingWindow {

        @BeforeEach
        void configureWrappingWindow() {
            config.setHighRiskStartHour(22); // 10 PM
            config.setHighRiskEndHour(4);    // 4 AM (wraps midnight)
            config.setFlagWeekends(false);   // isolate night-window logic
        }

        @Test
        @DisplayName("23:00 UTC (after midnight start) → MANUAL_REVIEW")
        void insideWindowAfterStart() {
            Instant at23 = Instant.parse("2026-06-09T23:00:00Z"); // Tuesday 23:00 UTC
            RuleResult result = rule.evaluate(txWith("gambling", at23));
            assertEquals(Verdict.MANUAL_REVIEW, result.getVerdict());
        }

        @Test
        @DisplayName("03:00 UTC (before wrapping end) → MANUAL_REVIEW")
        void insideWindowBeforeEnd() {
            RuleResult result = rule.evaluate(txWith("gambling", MON_3AM_UTC));
            assertEquals(Verdict.MANUAL_REVIEW, result.getVerdict());
        }

        @Test
        @DisplayName("10:00 UTC (outside wrapping window) → APPROVED")
        void outsideWrappingWindow() {
            RuleResult result = rule.evaluate(txWith("gambling", MON_10AM_UTC));
            assertEquals(Verdict.APPROVED, result.getVerdict());
        }
    }

    // ── All high-risk categories ──────────────────────────────────────────────

    @ParameterizedTest(name = "category={0} at night → MANUAL_REVIEW")
    @MethodSource("highRiskCategorySource")
    @DisplayName("All configured high-risk categories are flagged at night")
    void allHighRiskCategoriesFlaggedAtNight(String category) {
        RuleResult result = rule.evaluate(txWith(category, MON_3AM_UTC));
        assertEquals(Verdict.MANUAL_REVIEW, result.getVerdict(),
                "Category '" + category + "' should be flagged during night window");
    }

    static Stream<Arguments> highRiskCategorySource() {
        return Stream.of("gambling", "crypto", "wire-transfer", "adult", "forex")
                .map(Arguments::of);
    }

    // ── Case insensitivity ────────────────────────────────────────────────────

    @Test
    @DisplayName("Category matching is case-insensitive")
    void categoryMatchingIsCaseInsensitive() {
        RuleResult upper = rule.evaluate(txWith("GAMBLING", MON_3AM_UTC));
        RuleResult mixed = rule.evaluate(txWith("Gambling", MON_3AM_UTC));
        assertEquals(Verdict.MANUAL_REVIEW, upper.getVerdict(), "GAMBLING should match");
        assertEquals(Verdict.MANUAL_REVIEW, mixed.getVerdict(), "Gambling should match");
    }

    // ── Metadata ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Rule name is TIME_WINDOW")
    void ruleName() {
        assertEquals("TIME_WINDOW", rule.getName());
    }

    @Test
    @DisplayName("Rule order is 15 (between Blacklist=10 and AmountAnomaly=20)")
    void ruleOrder() {
        assertEquals(15, rule.getOrder());
    }
}
