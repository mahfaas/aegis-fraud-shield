package io.github.mahfaas.fraudshield.producer;

import io.github.mahfaas.fraudshield.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Generates synthetic transactions and sends them to Kafka.
 * <p>
 * Produces a mix of normal and anomalous transactions to demonstrate
 * all fraud-detection rules:
 * <ul>
 *   <li>70% — normal transactions</li>
 *   <li>10% — velocity anomalies (same account, rapid fire)</li>
 *   <li>10% — blacklisted IP/BIN</li>
 *   <li>5% — high amount anomalies</li>
 *   <li>5% — geo anomalies (different countries)</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class TransactionProducer {

    private static final List<String> NORMAL_IPS = List.of(
            "203.0.113.10", "198.51.100.25", "192.0.2.50", "100.64.0.1", "172.16.0.99"
    );
    private static final List<String> BLACKLISTED_IPS = List.of(
            "10.0.0.1", "192.168.1.100", "185.45.67.89"
    );
    private static final List<String> NORMAL_BINS = List.of(
            "411111", "422222", "433333", "455555", "466666"
    );
    private static final List<String> BLACKLISTED_BINS = List.of(
            "427600", "531234"
    );
    private static final List<String> COUNTRIES = List.of(
            "RU", "US", "DE", "GB", "CN", "JP", "BR"
    );
    private static final List<String> ACCOUNTS = List.of(
            "ACC-001", "ACC-002", "ACC-003", "ACC-004", "ACC-005",
            "ACC-006", "ACC-007", "ACC-008", "ACC-009", "ACC-010"
    );

    private final KafkaTemplate<String, Transaction> kafkaTemplate;
    private final String topicIn;
    private final Random random = new Random();

    public TransactionProducer(
            KafkaTemplate<String, Transaction> kafkaTemplate,
            @Value("${fraud.kafka.topic-in}") String topicIn) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicIn = topicIn;
    }

    /**
     * Generate and send a batch of synthetic transactions.
     *
     * @param count number of transactions to generate
     * @return number of transactions sent
     */
    public int generateBatch(int count) {
        int sent = 0;
        for (int i = 0; i < count; i++) {
            Transaction tx = generateTransaction(i, count);
            kafkaTemplate.send(topicIn, tx.getTransactionId(), tx);
            sent++;
        }
        log.info("Generated and sent {} transactions to topic '{}'", sent, topicIn);
        return sent;
    }

    private Transaction generateTransaction(int index, int total) {
        double ratio = (double) index / total;

        if (ratio < 0.70) {
            return buildNormal();
        } else if (ratio < 0.80) {
            return buildVelocityAnomaly();
        } else if (ratio < 0.90) {
            return buildBlacklisted();
        } else if (ratio < 0.95) {
            return buildHighAmount();
        } else {
            return buildGeoAnomaly();
        }
    }

    private Transaction buildNormal() {
        return Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .accountId("ACC-NORM-" + random.nextInt(100_000)) // Большой пул, чтобы не было коллизий Velocity
                .cardBin(randomFrom(NORMAL_BINS))
                .amount(BigDecimal.valueOf(100 + random.nextInt(50_000)))
                .currency("RUB")
                .merchantId("MERCH-" + (random.nextInt(50) + 1))
                .merchantCategory("general")
                .sourceIp(randomFrom(NORMAL_IPS))
                .country("RU")
                .timestamp(Instant.now())
                .build();
    }

    private Transaction buildVelocityAnomaly() {
        String account = "ACC-VELOCITY";
        return Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .accountId(account)
                .cardBin(randomFrom(NORMAL_BINS))
                .amount(BigDecimal.valueOf(500 + random.nextInt(5_000)))
                .currency("RUB")
                .merchantId("MERCH-" + (random.nextInt(10) + 1))
                .merchantCategory("online")
                .sourceIp(randomFrom(NORMAL_IPS))
                .country("RU")
                .timestamp(Instant.now())
                .build();
    }

    private Transaction buildBlacklisted() {
        boolean useIp = random.nextBoolean();
        return Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .accountId(randomFrom(ACCOUNTS))
                .cardBin(useIp ? randomFrom(NORMAL_BINS) : randomFrom(BLACKLISTED_BINS))
                .amount(BigDecimal.valueOf(1000 + random.nextInt(10_000)))
                .currency("RUB")
                .merchantId("MERCH-SUSPECT")
                .merchantCategory("gambling")
                .sourceIp(useIp ? randomFrom(BLACKLISTED_IPS) : randomFrom(NORMAL_IPS))
                .country("RU")
                .timestamp(Instant.now())
                .build();
    }

    private Transaction buildHighAmount() {
        return Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .accountId(randomFrom(ACCOUNTS))
                .cardBin(randomFrom(NORMAL_BINS))
                .amount(BigDecimal.valueOf(200_000 + random.nextInt(1_000_000)))
                .currency("RUB")
                .merchantId("MERCH-LUXURY")
                .merchantCategory("jewelry")
                .sourceIp(randomFrom(NORMAL_IPS))
                .country("RU")
                .timestamp(Instant.now())
                .build();
    }

    private Transaction buildGeoAnomaly() {
        String account = "ACC-GEO-" + random.nextInt(3);
        return Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .accountId(account)
                .cardBin(randomFrom(NORMAL_BINS))
                .amount(BigDecimal.valueOf(500 + random.nextInt(20_000)))
                .currency("USD")
                .merchantId("MERCH-FOREIGN")
                .merchantCategory("travel")
                .sourceIp(randomFrom(NORMAL_IPS))
                .country(randomFrom(COUNTRIES.subList(1, COUNTRIES.size()))) // не RU
                .timestamp(Instant.now())
                .build();
    }

    private <T> T randomFrom(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }
}
