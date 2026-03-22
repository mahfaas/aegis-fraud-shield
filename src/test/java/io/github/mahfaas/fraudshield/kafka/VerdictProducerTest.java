package io.github.mahfaas.fraudshield.kafka;

import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.Verdict;
import io.github.mahfaas.fraudshield.model.VerdictedTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VerdictProducerTest {

    @Mock
    private KafkaTemplate<String, VerdictedTransaction> verdictTemplate;

    @Mock
    private KafkaTemplate<String, String> dlqTemplate;

    @Test
    @DisplayName("Should send verdicted transaction to output topic")
    void shouldSendVerdict() {
        VerdictProducer producer = new VerdictProducer(
                verdictTemplate, dlqTemplate,
                "transactions-verdicted", "transactions-dlq"
        );

        Transaction tx = Transaction.builder()
                .transactionId("tx-001")
                .accountId("ACC-001")
                .cardBin("411111")
                .amount(BigDecimal.valueOf(1000))
                .currency("RUB")
                .country("RU")
                .timestamp(Instant.now())
                .build();

        VerdictedTransaction verdicted = VerdictedTransaction.builder()
                .transaction(tx)
                .verdict(Verdict.APPROVED)
                .reasons(Collections.emptyList())
                .processedAt(Instant.now())
                .build();

        producer.send(verdicted);

        verify(verdictTemplate).send("transactions-verdicted", "tx-001", verdicted);
    }

    @Test
    @DisplayName("Should send failed message to DLQ")
    void shouldSendToDlq() {
        VerdictProducer producer = new VerdictProducer(
                verdictTemplate, dlqTemplate,
                "transactions-verdicted", "transactions-dlq"
        );

        producer.sendToDlq("tx-invalid", "Validation failed");

        verify(dlqTemplate).send(
                org.mockito.ArgumentMatchers.eq("transactions-dlq"),
                org.mockito.ArgumentMatchers.contains("Validation failed")
        );
    }
}
