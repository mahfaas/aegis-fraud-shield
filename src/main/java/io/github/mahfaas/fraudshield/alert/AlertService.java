package io.github.mahfaas.fraudshield.alert;

import io.github.mahfaas.fraudshield.metrics.FraudMetrics;
import io.github.mahfaas.fraudshield.model.Verdict;
import io.github.mahfaas.fraudshield.model.VerdictedTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Notifies an external webhook whenever the Rule Engine produces a
 * {@code DECLINED} or {@code MANUAL_REVIEW} verdict.
 *
 * <p>Called from {@link io.github.mahfaas.fraudshield.kafka.TransactionConsumer}
 * right after the verdict is persisted to the audit log. A failure here must
 * never propagate back into the Kafka listener — a slow or unreachable
 * webhook endpoint should not stall transaction processing — so all
 * {@link RestClientException}s are caught and logged.
 */
@Slf4j
@Service
public class AlertService {

    private final AlertProperties properties;
    private final FraudMetrics metrics;

    /** Package-private (not private) so {@code AlertServiceTest} can bind a {@code MockRestServiceServer} to it. */
    final RestTemplate restTemplate;

    public AlertService(AlertProperties properties, FraudMetrics metrics, RestTemplateBuilder builder) {
        this.properties = properties;
        this.metrics = metrics;
        this.restTemplate = builder
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
    }

    /**
     * Sends an alert for DECLINED / MANUAL_REVIEW verdicts. No-op if alerting is disabled
     * or the verdict is APPROVED.
     *
     * @param result the verdicted transaction produced by the Rule Engine
     */
    public void notify(VerdictedTransaction result) {
        if (!properties.isEnabled() || result.getVerdict() == Verdict.APPROVED) {
            return;
        }

        AlertPayload payload = AlertPayload.from(result);

        try {
            restTemplate.postForEntity(properties.getWebhookUrl(), payload, Void.class);
            metrics.recordAlertSent();
        } catch (RestClientException e) {
            log.warn("Failed to send fraud alert for txId={}: {}",
                    result.getTransaction().getTransactionId(), e.getMessage());
            metrics.recordAlertFailed();
        }
    }

    /**
     * Minimal outbound alert payload — deliberately excludes sensitive fields
     * (card BIN, source IP) present on the full {@link VerdictedTransaction}.
     */
    record AlertPayload(
            String transactionId,
            String accountId,
            BigDecimal amount,
            Verdict verdict,
            int totalRiskScore,
            List<String> reasons,
            Instant processedAt
    ) {
        static AlertPayload from(VerdictedTransaction result) {
            return new AlertPayload(
                    result.getTransaction().getTransactionId(),
                    result.getTransaction().getAccountId(),
                    result.getTransaction().getAmount(),
                    result.getVerdict(),
                    result.getTotalRiskScore(),
                    result.getReasons(),
                    result.getProcessedAt()
            );
        }
    }
}
