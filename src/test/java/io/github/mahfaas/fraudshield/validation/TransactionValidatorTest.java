package io.github.mahfaas.fraudshield.validation;

import io.github.mahfaas.fraudshield.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionValidatorTest {

    private TransactionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TransactionValidator();
    }

    private Transaction.TransactionBuilder validTransaction() {
        return Transaction.builder()
                .transactionId("tx-001")
                .accountId("ACC-001")
                .cardBin("411111")
                .amount(BigDecimal.valueOf(1000))
                .currency("RUB")
                .country("RU")
                .sourceIp("8.8.8.8")
                .timestamp(Instant.now());
    }

    @Test
    @DisplayName("Should pass valid transaction")
    void shouldPassValidTransaction() {
        assertTrue(validator.isValid(validTransaction().build()));
    }

    @Test
    @DisplayName("Should fail on null transaction")
    void shouldFailOnNull() {
        List<String> errors = validator.validate(null);
        assertEquals(1, errors.size());
        assertTrue(errors.getFirst().contains("null"));
    }

    @Test
    @DisplayName("Should fail on blank transactionId")
    void shouldFailOnBlankTransactionId() {
        Transaction tx = validTransaction().transactionId("").build();
        List<String> errors = validator.validate(tx);
        assertTrue(errors.stream().anyMatch(e -> e.contains("transactionId")));
    }

    @Test
    @DisplayName("Should fail on null amount")
    void shouldFailOnNullAmount() {
        Transaction tx = validTransaction().amount(null).build();
        List<String> errors = validator.validate(tx);
        assertTrue(errors.stream().anyMatch(e -> e.contains("amount")));
    }

    @Test
    @DisplayName("Should fail on zero amount")
    void shouldFailOnZeroAmount() {
        Transaction tx = validTransaction().amount(BigDecimal.ZERO).build();
        assertFalse(validator.isValid(tx));
    }

    @Test
    @DisplayName("Should fail on negative amount")
    void shouldFailOnNegativeAmount() {
        Transaction tx = validTransaction().amount(BigDecimal.valueOf(-100)).build();
        assertFalse(validator.isValid(tx));
    }

    @Test
    @DisplayName("Should collect multiple errors")
    void shouldCollectMultipleErrors() {
        Transaction tx = Transaction.builder().build(); // everything null
        List<String> errors = validator.validate(tx);
        assertTrue(errors.size() > 3, "Expected multiple validation errors, got: " + errors);
    }
}
