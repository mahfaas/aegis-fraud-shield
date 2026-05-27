package io.github.mahfaas.fraudshield.metrics;

import io.github.mahfaas.fraudshield.engine.RuleEngine;
import io.github.mahfaas.fraudshield.model.Verdict;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized Micrometer metrics for the fraud-detection pipeline.
 * <p>
 * Tracks transaction throughput, rule evaluations, verdicts, and processing latency.
 * All metrics are exposed via the /actuator/prometheus endpoint.
 * </p>
 *
 * <p><strong>Metrics registered:</strong></p>
 * <ul>
 *   <li>{@code fraud.transactions.received} — total received from Kafka</li>
 *   <li>{@code fraud.transactions.verdicted{verdict}} — broken down by verdict tag</li>
 *   <li>{@code fraud.transactions.dlq} — routed to dead letter queue</li>
 *   <li>{@code fraud.rules.triggered{rule}} — per-rule and aggregate trigger counts</li>
 *   <li>{@code fraud.processing.time} — processing latency histogram (P50/P95/P99)</li>
 *   <li>{@code fraud.engine.active_rules} — gauge: number of loaded rules</li>
 * </ul>
 */
@Component
public class FraudMetrics {

    private final Counter transactionsReceived;
    private final Counter transactionsApproved;
    private final Counter transactionsDeclined;
    private final Counter transactionsManualReview;
    private final Counter transactionsDlq;
    private final Counter ruleTriggered;
    private final Timer processingTime;
    private final MeterRegistry registry;

    /** Cache of per-rule counters to avoid repeated registry lookups. */
    private final ConcurrentHashMap<String, Counter> ruleCounters = new ConcurrentHashMap<>();

    /**
     * @param ruleEngine injected lazily to break the circular dependency:
     *                   RuleEngine → FraudMetrics → RuleEngine.
     */
    public FraudMetrics(MeterRegistry registry, @Lazy RuleEngine ruleEngine) {
        this.registry = registry;

        this.transactionsReceived = Counter.builder("fraud.transactions.received")
                .description("Total transactions received from Kafka")
                .register(registry);

        this.transactionsApproved = Counter.builder("fraud.transactions.verdicted")
                .tag("verdict", "APPROVED")
                .description("Transactions approved")
                .register(registry);

        this.transactionsDeclined = Counter.builder("fraud.transactions.verdicted")
                .tag("verdict", "DECLINED")
                .description("Transactions declined")
                .register(registry);

        this.transactionsManualReview = Counter.builder("fraud.transactions.verdicted")
                .tag("verdict", "MANUAL_REVIEW")
                .description("Transactions sent to manual review")
                .register(registry);

        this.transactionsDlq = Counter.builder("fraud.transactions.dlq")
                .description("Transactions sent to dead letter queue")
                .register(registry);

        this.ruleTriggered = Counter.builder("fraud.rules.triggered")
                .tag("rule", "ALL")
                .description("Aggregate rule triggers (decline or manual review)")
                .register(registry);

        this.processingTime = Timer.builder("fraud.processing.time")
                .description("Time to process a single transaction through the rule engine")
                .register(registry);

        // Gauge: reports the live count of registered rules via RuleEngine::getRuleCount
        Gauge.builder("fraud.engine.active_rules", ruleEngine, RuleEngine::getRuleCount)
                .description("Number of fraud-detection rules currently loaded in the engine")
                .register(registry);
    }

    public void recordReceived() {
        transactionsReceived.increment();
    }

    public void recordVerdict(Verdict verdict) {
        switch (verdict) {
            case APPROVED -> transactionsApproved.increment();
            case DECLINED -> transactionsDeclined.increment();
            case MANUAL_REVIEW -> transactionsManualReview.increment();
        }
    }

    public void recordDlq() {
        transactionsDlq.increment();
    }

    /** Increments the aggregate rule-triggered counter. */
    public void recordRuleTriggered() {
        ruleTriggered.increment();
    }

    /**
     * Increments both the aggregate counter and a per-rule counter tagged by {@code ruleName}.
     * Per-rule counters appear in Prometheus as
     * {@code fraud_rules_triggered_total{rule="RULE_NAME"}}.
     *
     * @param ruleName the name returned by {@link io.github.mahfaas.fraudshield.engine.Rule#getName()}
     */
    public void recordRuleTriggered(String ruleName) {
        ruleTriggered.increment();
        ruleCounters
                .computeIfAbsent(ruleName, name ->
                        Counter.builder("fraud.rules.triggered")
                                .tag("rule", name)
                                .description("Rule triggers for rule: " + name)
                                .register(registry))
                .increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start();
    }

    public void stopTimer(Timer.Sample sample) {
        sample.stop(processingTime);
    }

    // ── Snapshot getters for MetricsController /summary endpoint ─────────────

    public long getReceivedCount()     { return (long) transactionsReceived.count(); }
    public long getApprovedCount()     { return (long) transactionsApproved.count(); }
    public long getDeclinedCount()     { return (long) transactionsDeclined.count(); }
    public long getManualReviewCount() { return (long) transactionsManualReview.count(); }
    public long getDlqCount()          { return (long) transactionsDlq.count(); }
}
