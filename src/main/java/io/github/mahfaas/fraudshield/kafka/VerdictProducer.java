package io.github.mahfaas.fraudshield.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mahfaas.fraudshield.model.VerdictedTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes fraud-check results to Kafka.
 * <p>
 * Verdicted transactions go to the output topic.
 * Failed / invalid messages go to the Dead Letter Queue.
 * </p>
 */
@Slf4j
@Component
public class VerdictProducer {

    private final KafkaTemplate<String, VerdictedTransaction> verdictTemplate;
    private final KafkaTemplate<String, String> dlqTemplate;
    private final ObjectMapper objectMapper;
    private final String topicOut;
    private final String topicDlq;

    public VerdictProducer(
            KafkaTemplate<String, VerdictedTransaction> verdictTemplate,
            KafkaTemplate<String, String> dlqTemplate,
            ObjectMapper objectMapper,
            @Value("${fraud.kafka.topic-out}") String topicOut,
            @Value("${fraud.kafka.topic-dlq}") String topicDlq) {
        this.verdictTemplate = verdictTemplate;
        this.dlqTemplate = dlqTemplate;
        this.objectMapper = objectMapper;
        this.topicOut = topicOut;
        this.topicDlq = topicDlq;
    }

    /**
     * Publish a verdicted transaction to the output topic.
     */
    public void send(VerdictedTransaction verdictedTransaction) {
        String key = verdictedTransaction.getTransaction().getTransactionId();
        verdictTemplate.send(topicOut, key, verdictedTransaction);
        log.info("Published verdict for txId={}: {}", key, verdictedTransaction.getVerdict());
    }

    /**
     * Route an invalid or failed message to the Dead Letter Queue.
     * Uses Jackson ObjectMapper for safe JSON serialization.
     */
    public void sendToDlq(String failedPayload, String reason) {
        try {
            String message = objectMapper.writeValueAsString(
                    Map.of("payload", failedPayload, "reason", reason));
            dlqTemplate.send(topicDlq, message);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize DLQ message for payload={}", failedPayload, e);
            dlqTemplate.send(topicDlq, "{\"payload\":\"" + failedPayload + "\",\"error\":\"serialization_failed\"}");
        }
        log.warn("Sent message to DLQ: {}", reason);
    }
}
