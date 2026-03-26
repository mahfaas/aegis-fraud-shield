package io.github.mahfaas.fraudshield.kafka;

import io.github.mahfaas.fraudshield.engine.RuleEngine;
import io.github.mahfaas.fraudshield.metrics.FraudMetrics;
import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.VerdictedTransaction;
import io.github.mahfaas.fraudshield.validation.TransactionValidator;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Consumes raw transactions from Kafka, validates and evaluates them.
 * <p>
 * Valid transactions flow through the Rule Engine.
 * Invalid transactions are routed to the Dead Letter Queue.
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

    @KafkaListener(topics = "${fraud.kafka.topic-in}", groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
    public void consume(Transaction transaction) {
        log.info("Received transaction: txId={}", transaction.getTransactionId());
        metrics.recordReceived();

        List<String> validationErrors = validator.validate(transaction);

        if (!validationErrors.isEmpty()) {
            log.warn("Validation failed for txId={}: {}", transaction.getTransactionId(), validationErrors);
            verdictProducer.sendToDlq(
                    transaction.getTransactionId(),
                    "Validation failed: " + String.join("; ", validationErrors));
            metrics.recordDlq();
            return;
        }

        Timer.Sample timer = metrics.startTimer();
        VerdictedTransaction result = ruleEngine.evaluate(transaction);
        metrics.stopTimer(timer);

        verdictProducer.send(result);
        metrics.recordVerdict(result.getVerdict());

        log.info("Processed txId={} → verdict={}", transaction.getTransactionId(), result.getVerdict());
    }
}
