package io.github.mahfaas.fraudshield.validation;

import io.github.mahfaas.fraudshield.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates the structural correctness of a transaction before it enters the Rule Engine.
 * <p>
 * Invalid transactions are routed to the DLQ (Dead Letter Queue) instead of being processed.
 * </p>
 */
@Slf4j
@Component
public class TransactionValidator {

    /**
     * Validate a transaction and return a list of error messages.
     *
     * @param transaction the transaction to validate
     * @return empty list if valid, otherwise list of validation errors
     */
    public List<String> validate(Transaction transaction) {
        List<String> errors = new ArrayList<>();

        if (transaction == null) {
            errors.add("Transaction is null");
            return errors;
        }

        if (isBlank(transaction.getTransactionId())) {
            errors.add("transactionId is required");
        }

        if (isBlank(transaction.getAccountId())) {
            errors.add("accountId is required");
        }

        if (isBlank(transaction.getCardBin())) {
            errors.add("cardBin is required");
        }

        if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("amount must be positive");
        }

        if (isBlank(transaction.getCurrency())) {
            errors.add("currency is required");
        }

        if (isBlank(transaction.getCountry())) {
            errors.add("country is required");
        }

        if (transaction.getTimestamp() == null) {
            errors.add("timestamp is required");
        }

        if (!errors.isEmpty()) {
            log.warn("Transaction validation failed for txId={}: {}",
                    transaction.getTransactionId(), errors);
        }

        return errors;
    }

    /**
     * @return true if the transaction is valid (no errors).
     */
    public boolean isValid(Transaction transaction) {
        return validate(transaction).isEmpty();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
