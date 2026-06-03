package io.github.mahfaas.fraudshield.kafka;

import io.github.mahfaas.fraudshield.audit.AuditService;
import io.github.mahfaas.fraudshield.engine.RuleEngine;
import io.github.mahfaas.fraudshield.metrics.FraudMetrics;
import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.VerdictedTransaction;
import io.github.mahfaas.fraudshield.validation.TransactionValidator;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Consumes raw transactions from Kafka, validates and evaluates them.
 * <p>
 * Processing pipeline for each message:
 * <ol>
 *   <li><strong>MDC setup</strong> — populates {@code transactionId} and
 *       {@code accountId} in the Mapped Diagnostic Context so every log line
 *       within this thread automatically includes these fields.</li>
 *   <li><strong>Idempotency check</strong> — skips duplicate messages that
 *       were re-delivered by Kafka (at-least-once guarantee).</li>
 *   <li><strong>Validation</strong> — structural checks; invalid messages
 *       go to the Dead Letter Queue.</li>
 *   <li><strong>Rule Engine</strong> — evaluates all fraud-detection rules
 *       and produces a {@link VerdictedTransaction}.</li>
 *   <li><strong>Publish verdict</strong> — result is sent to the output topic.</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionConsumer {

    private final TransactionValidator validator;
    private final RuleEngine ruleEngine;
    private final VerdictProducer verdictProducer;
    private final FraudMetrics metrics;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;

    @KafkaListener(
            topics = "${fraud.kafka.topic-in}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(Transaction transaction) {
        // ── 6c: MDC — every log line in this thread gets txId + accountId ──────
        MDC.put("transactionId", transaction.getTransactionId());
        MDC.put("accountId", transaction.getAccountId());

        try {
            log.info("Received transaction: txId={}", transaction.getTransactionId());
            metrics.recordReceived();

            // ── 6b: Idempotency — skip if already processed ───────────────────
            if (idempotencyService.isDuplicate(transaction.getTransactionId())) {
                log.warn("Skipping duplicate transaction: txId={}", transaction.getTransactionId());
                return;
            }

            // ── Validation ────────────────────────────────────────────────────
            List<String> validationErrors = validator.validate(transaction);
            if (!validationErrors.isEmpty()) {
                log.warn("Validation failed for txId={}: {}", transaction.getTransactionId(), validationErrors);
                verdictProducer.sendToDlq(
                        transaction.getTransactionId(),
                        "Validation failed: " + String.join("; ", validationErrors));
                metrics.recordDlq();
                return;
            }

            // ── Rule Engine ───────────────────────────────────────────────────
            Timer.Sample timer = metrics.startTimer();
            VerdictedTransaction result = ruleEngine.evaluate(transaction);
            metrics.stopTimer(timer);

            verdictProducer.send(result);
            auditService.record(result);           // persist to audit_log
            metrics.recordVerdict(result.getVerdict());

            log.info("Processed txId={} → verdict={} riskScore={}",
                    transaction.getTransactionId(), result.getVerdict(), result.getTotalRiskScore());

        } finally {
            // ── 6c: Always clear MDC to avoid context leaking to the next message ──
            MDC.clear();
        }
    }
}
