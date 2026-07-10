package io.github.mahfaas.fraudshield.kafka;

import io.github.mahfaas.fraudshield.alert.AlertService;
import io.github.mahfaas.fraudshield.audit.AuditService;
import io.github.mahfaas.fraudshield.engine.RuleEngine;
import io.github.mahfaas.fraudshield.metrics.FraudMetrics;
import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.Verdict;
import io.github.mahfaas.fraudshield.model.VerdictedTransaction;
import io.github.mahfaas.fraudshield.validation.TransactionValidator;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionConsumerTest {

    @Mock private TransactionValidator validator;
    @Mock private RuleEngine ruleEngine;
    @Mock private VerdictProducer verdictProducer;
    @Mock private FraudMetrics metrics;
    @Mock private IdempotencyService idempotencyService;
    @Mock private AuditService auditService;
    @Mock private AlertService alertService;

    @InjectMocks
    private TransactionConsumer consumer;

    @BeforeEach
    void setUp() {
        lenient().when(metrics.startTimer()).thenReturn(mock(Timer.Sample.class));
        // Default: not a duplicate
        lenient().when(idempotencyService.isDuplicate(anyString())).thenReturn(false);
    }

    private Transaction validTransaction() {
        return Transaction.builder()
                .transactionId("tx-001")
                .accountId("ACC-001")
                .cardBin("411111")
                .amount(BigDecimal.valueOf(1000))
                .currency("RUB")
                .country("RU")
                .sourceIp("8.8.8.8")
                .timestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should route valid transaction through RuleEngine and publish verdict")
    void shouldProcessValidTransaction() {
        Transaction tx = validTransaction();
        VerdictedTransaction verdict = VerdictedTransaction.builder()
                .transaction(tx).verdict(Verdict.APPROVED)
                .reasons(Collections.emptyList()).totalRiskScore(0)
                .processedAt(Instant.now()).build();

        when(validator.validate(tx)).thenReturn(Collections.emptyList());
        when(ruleEngine.evaluate(tx)).thenReturn(verdict);

        consumer.consume(tx);

        verify(ruleEngine).evaluate(tx);
        verify(verdictProducer).send(verdict);
        verify(verdictProducer, never()).sendToDlq(anyString(), anyString());
        verify(auditService).record(verdict);
        verify(alertService).notify(verdict);
    }

    @Test
    @DisplayName("Should route invalid transaction to DLQ")
    void shouldRouteInvalidToDlq() {
        Transaction tx = validTransaction();
        when(validator.validate(tx)).thenReturn(List.of("amount must be positive"));

        consumer.consume(tx);

        verify(verdictProducer).sendToDlq(anyString(), anyString());
        verify(ruleEngine, never()).evaluate(any());
        verify(verdictProducer, never()).send(any());
        verify(alertService, never()).notify(any());
    }

    @Test
    @DisplayName("Should not call RuleEngine for invalid transaction")
    void shouldNotCallRuleEngineForInvalid() {
        Transaction tx = validTransaction();
        when(validator.validate(tx)).thenReturn(List.of("error1", "error2"));

        consumer.consume(tx);

        verify(ruleEngine, never()).evaluate(any());
    }

    @Test
    @DisplayName("Should skip duplicate transactions without calling RuleEngine")
    void shouldSkipDuplicateTransaction() {
        Transaction tx = validTransaction();
        when(idempotencyService.isDuplicate(tx.getTransactionId())).thenReturn(true);

        consumer.consume(tx);

        verify(ruleEngine, never()).evaluate(any());
        verify(verdictProducer, never()).send(any());
        verify(verdictProducer, never()).sendToDlq(anyString(), anyString());
        verify(validator, never()).validate(any());
    }

    @Test
    @DisplayName("Should check idempotency before validation")
    void shouldCheckIdempotencyBeforeValidation() {
        Transaction tx = validTransaction();
        when(idempotencyService.isDuplicate(anyString())).thenReturn(true);

        consumer.consume(tx);

        // Idempotency short-circuits — validator must not be called
        verify(idempotencyService).isDuplicate(tx.getTransactionId());
        verify(validator, never()).validate(any());
    }
}
