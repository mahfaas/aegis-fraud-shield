package io.github.mahfaas.fraudshield.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Configuration properties for the {@link io.github.mahfaas.fraudshield.engine.rules.TimeWindowRule}.
 *
 * <p>Bound from the {@code fraud.rules.time-window.*} namespace in {@code application.yaml}.
 * Spring's {@code @ConfigurationProperties} gives us type-safe, IDE-navigable config
 * with automatic validation — a cleaner approach than {@code @Value} for multi-field groups.
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>{@code enabled} — set to {@code false} to disable the rule entirely</li>
 *   <li>{@code high-risk-start-hour} — start of the high-risk night window (inclusive, 24h clock)</li>
 *   <li>{@code high-risk-end-hour}   — end of the high-risk night window (exclusive)</li>
 *   <li>{@code high-risk-categories} — merchant categories that trigger the rule</li>
 *   <li>{@code flag-weekends}        — if {@code true}, high-risk category txns on weekends are flagged</li>
 * </ul>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fraud.rules.time-window")
public class TimeWindowRuleConfig {

    /** Whether this rule is active. */
    private boolean enabled = true;

    /**
     * Start of the nightly high-risk window (inclusive, 24h clock, UTC).
     * Default: 2 AM.
     */
    private int highRiskStartHour = 2;

    /**
     * End of the nightly high-risk window (exclusive, 24h clock, UTC).
     * Default: 5 AM.
     */
    private int highRiskEndHour = 5;

    /**
     * Merchant categories considered high-risk for time-based detection.
     * Transactions in these categories outside business hours are suspicious.
     */
    private List<String> highRiskCategories = List.of(
            "gambling", "crypto", "wire-transfer", "adult", "forex"
    );

    /**
     * If {@code true}, high-risk category transactions on weekends
     * (Saturday and Sunday UTC) are also flagged regardless of hour.
     */
    private boolean flagWeekends = true;
}
