package io.github.mahfaas.fraudshield.engine.rules;

import io.github.mahfaas.fraudshield.config.TimeWindowRuleConfig;
import io.github.mahfaas.fraudshield.engine.Rule;
import io.github.mahfaas.fraudshield.engine.RuleResult;
import io.github.mahfaas.fraudshield.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Detects transactions made during high-risk time windows from risky merchant categories.
 *
 * <h3>Business rationale</h3>
 * <p>
 * Statistical fraud analysis consistently shows that transactions in certain merchant
 * categories (gambling, crypto, wire-transfer) made between 2 AM and 5 AM UTC, or on
 * weekends, are disproportionately associated with fraud. A legitimate customer is
 * unlikely to initiate a wire transfer at 3 AM on a Sunday.
 * </p>
 *
 * <h3>Detection logic</h3>
 * <ol>
 *   <li>If the merchant category is not in the high-risk list → immediately pass ({@code APPROVED}).</li>
 *   <li>If the transaction timestamp falls within the nightly window
 *       [{@code highRiskStartHour}, {@code highRiskEndHour}) UTC → {@code MANUAL_REVIEW}.</li>
 *   <li>If {@code flagWeekends=true} and the transaction is on Saturday or Sunday UTC → {@code MANUAL_REVIEW}.</li>
 *   <li>Otherwise → {@code APPROVED}.</li>
 * </ol>
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li>Zero external dependencies (no Redis, no DB) — purely deterministic, ideal for unit-testing.</li>
 *   <li>Uses {@code java.time.ZonedDateTime} with explicit {@link ZoneOffset#UTC} to avoid
 *       timezone surprises on servers deployed in different regions.</li>
 *   <li>Config is injected via {@link TimeWindowRuleConfig} ({@code @ConfigurationProperties}),
 *       demonstrating the type-safe alternative to {@code @Value} for multi-field groups.</li>
 * </ul>
 *
 * <h3>Interview talking point</h3>
 * <p>
 * This rule contributes a {@code riskScore} of 50 (MANUAL_REVIEW).
 * When combined with another soft-warning rule (e.g. GeoVelocityRule also returning 50),
 * the {@link RiskScoreThresholdRule} composite scorer will see a total of 100 and
 * escalate to {@code DECLINED} — demonstrating how weak individual signals combine
 * into a decisive fraud verdict without any single rule being definitive.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "fraud.rules.time-window.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TimeWindowRule implements Rule {

    public static final String RULE_NAME = "TIME_WINDOW";

    private final TimeWindowRuleConfig config;

    @Override
    public RuleResult evaluate(Transaction transaction) {
        String category = transaction.getMerchantCategory();

        // Fast-path: category not high-risk → no action
        if (category == null || !isHighRiskCategory(category)) {
            return RuleResult.approve(RULE_NAME);
        }

        ZonedDateTime txTime = transaction.getTimestamp()
                .atZone(ZoneOffset.UTC);

        // Check nightly window
        if (isNightWindow(txTime)) {
            log.info("TimeWindowRule: txId={} category='{}' at hour {} UTC is in high-risk night window [{}–{})",
                    transaction.getTransactionId(), category,
                    txTime.getHour(), config.getHighRiskStartHour(), config.getHighRiskEndHour());
            return RuleResult.manualReview(RULE_NAME,
                    String.format("High-risk category '%s' transaction at %02d:%02d UTC " +
                                    "is within night window [%02d:00–%02d:00)",
                            category, txTime.getHour(), txTime.getMinute(),
                            config.getHighRiskStartHour(), config.getHighRiskEndHour()));
        }

        // Check weekend
        if (config.isFlagWeekends() && isWeekend(txTime)) {
            log.info("TimeWindowRule: txId={} category='{}' on {} UTC is a weekend transaction",
                    transaction.getTransactionId(), category, txTime.getDayOfWeek());
            return RuleResult.manualReview(RULE_NAME,
                    String.format("High-risk category '%s' transaction on %s UTC is flagged for weekend review",
                            category, txTime.getDayOfWeek()));
        }

        return RuleResult.approve(RULE_NAME);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean isHighRiskCategory(String category) {
        return config.getHighRiskCategories().stream()
                .anyMatch(c -> c.equalsIgnoreCase(category));
    }

    /**
     * Returns true if the given UTC time falls within the configured night window.
     * The window wraps midnight: start=22, end=4 → covers 22:00–04:00 UTC.
     */
    private boolean isNightWindow(ZonedDateTime utcTime) {
        int hour = utcTime.getHour();
        int start = config.getHighRiskStartHour();
        int end = config.getHighRiskEndHour();

        if (start < end) {
            // Normal window: e.g. 2–5 AM
            return hour >= start && hour < end;
        } else {
            // Wrapping window: e.g. 22 PM – 4 AM (crosses midnight)
            return hour >= start || hour < end;
        }
    }

    private boolean isWeekend(ZonedDateTime utcTime) {
        DayOfWeek day = utcTime.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    @Override
    public String getName() {
        return RULE_NAME;
    }

    /**
     * Order 15 — runs after {@link BlacklistRule} (10) but before
     * {@link AmountAnomalyRule} (20). Blacklist is the fastest decliner;
     * time-window is checked next as a cheap, DB-free operation.
     */
    @Override
    public int getOrder() {
        return 15;
    }
}
