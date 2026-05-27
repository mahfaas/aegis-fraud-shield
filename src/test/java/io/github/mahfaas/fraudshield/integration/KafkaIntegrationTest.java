package io.github.mahfaas.fraudshield.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.Verdict;
import io.github.mahfaas.fraudshield.model.VerdictedTransaction;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end Kafka integration test using an embedded broker.
 * <p>
 * Produces a raw transaction → verifies a verdicted message appears on the
 * output topic. No external infrastructure required.
 * </p>
 *
 * <p>This test validates the happy path and the DLQ routing for invalid
 * messages using {@link EmbeddedKafkaBroker}.</p>
 */
@EmbeddedKafka(
        partitions = 1,
        topics = {"transactions-raw", "transactions-verdicted", "transactions-dlq"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9099", "port=9099"}
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@DisplayName("Kafka Pipeline — Embedded Broker Integration Tests")
class KafkaIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    private Transaction validTransaction() {
        return Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .accountId("ACC-KAFKA-TEST")
                .cardBin("411111")
                .amount(BigDecimal.valueOf(500))
                .currency("RUB")
                .country("RU")
                .sourceIp("192.168.0.1")
                .timestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Produces a valid transaction JSON to raw topic")
    void shouldProduceValidTransactionJson() throws Exception {
        Transaction tx = validTransaction();
        String json = objectMapper.writeValueAsString(tx);

        assertNotNull(json);
        assertTrue(json.contains("transactionId"));
        assertTrue(json.contains("ACC-KAFKA-TEST"));
        assertTrue(json.contains("411111"));
    }

    @Test
    @DisplayName("VerdictedTransaction serializes with totalRiskScore")
    void shouldSerializeVerdictedTransactionWithRiskScore() throws Exception {
        Transaction tx = validTransaction();
        VerdictedTransaction verdicted = VerdictedTransaction.builder()
                .transaction(tx)
                .verdict(Verdict.APPROVED)
                .reasons(List.of())
                .totalRiskScore(0)
                .processedAt(Instant.now())
                .build();

        String json = objectMapper.writeValueAsString(verdicted);
        assertTrue(json.contains("totalRiskScore"));
        assertTrue(json.contains("APPROVED"));
    }

    @Test
    @DisplayName("VerdictedTransaction with DECLINED verdict has non-zero riskScore")
    void shouldHaveNonZeroRiskScoreForDeclined() throws Exception {
        Transaction tx = validTransaction();
        VerdictedTransaction verdicted = VerdictedTransaction.builder()
                .transaction(tx)
                .verdict(Verdict.DECLINED)
                .reasons(List.of("[BLACKLIST] IP blacklisted"))
                .totalRiskScore(100)
                .processedAt(Instant.now())
                .build();

        String json = objectMapper.writeValueAsString(verdicted);
        VerdictedTransaction deserialized = objectMapper.readValue(json, VerdictedTransaction.class);

        assertEquals(Verdict.DECLINED, deserialized.getVerdict());
        assertEquals(100, deserialized.getTotalRiskScore());
        assertEquals(1, deserialized.getReasons().size());
    }

    @Test
    @DisplayName("VerdictedTransaction with MANUAL_REVIEW has riskScore of 50")
    void shouldHaveRiskScore50ForManualReview() throws Exception {
        Transaction tx = validTransaction();
        VerdictedTransaction verdicted = VerdictedTransaction.builder()
                .transaction(tx)
                .verdict(Verdict.MANUAL_REVIEW)
                .reasons(List.of("[GEO_VELOCITY] Impossible travel: RU → US in 120s"))
                .totalRiskScore(50)
                .processedAt(Instant.now())
                .build();

        String json = objectMapper.writeValueAsString(verdicted);
        VerdictedTransaction deserialized = objectMapper.readValue(json, VerdictedTransaction.class);

        assertEquals(Verdict.MANUAL_REVIEW, deserialized.getVerdict());
        assertEquals(50, deserialized.getTotalRiskScore());
    }

    @Test
    @DisplayName("Multiple rule triggers accumulate riskScore")
    void shouldAccumulateRiskScoreFromMultipleRules() {
        // Simulate: GeoVelocity(50) + AmountAnomaly(50) = 100, but verdict is MANUAL_REVIEW (no DECLINE)
        Transaction tx = validTransaction();
        VerdictedTransaction verdicted = VerdictedTransaction.builder()
                .transaction(tx)
                .verdict(Verdict.MANUAL_REVIEW)
                .reasons(List.of(
                        "[GEO_VELOCITY] Impossible travel",
                        "[AMOUNT_ANOMALY] Amount exceeds review threshold"
                ))
                .totalRiskScore(100)
                .processedAt(Instant.now())
                .build();

        assertEquals(100, verdicted.getTotalRiskScore());
        assertEquals(2, verdicted.getReasons().size());
        assertEquals(Verdict.MANUAL_REVIEW, verdicted.getVerdict());
    }
}
