package io.github.mahfaas.fraudshield.metrics;

import io.github.mahfaas.fraudshield.model.Verdict;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Centralized Micrometer metrics for the fraud-detection pipeline.
 * <p>
 * Tracks transaction throughput, rule evaluations, verdicts, and processing latency.
 * All metrics are exposed via the /actuator/prometheus endpoint.
 * </p>
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

    public FraudMetrics(MeterRegistry registry) {
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
                .description("Total rule triggers (decline or manual review)")
                .register(registry);

        this.processingTime = Timer.builder("fraud.processing.time")
                .description("Time to process a single transaction through the rule engine")
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

    public void recordRuleTriggered() {
        ruleTriggered.increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start();
    }

    public void stopTimer(Timer.Sample sample) {
        sample.stop(processingTime);
    }
}
