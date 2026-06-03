package io.github.mahfaas.fraudshield.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Thrown when an incoming transaction fails structural validation.
 *
 * <p>Carries the full list of violation messages collected by
 * {@link io.github.mahfaas.fraudshield.validation.TransactionValidator} so the
 * caller (or the error response) can surface every problem at once rather than
 * failing on the first one.
 *
 * <p>Maps to HTTP {@code 422 Unprocessable Entity} — the payload was syntactically
 * valid JSON but its business-level content is unacceptable.
 */
public class TransactionValidationException extends FraudShieldException {

    private final List<String> violations;

    /**
     * @param transactionId the ID of the transaction that failed validation
     * @param violations    non-empty list of validation error messages
     */
    public TransactionValidationException(String transactionId, List<String> violations) {
        super(
                "Transaction validation failed for txId=" + transactionId
                        + ": " + String.join("; ", violations),
                HttpStatus.UNPROCESSABLE_ENTITY
        );
        this.violations = List.copyOf(violations);
    }

    /**
     * @return immutable list of individual violation messages.
     */
    public List<String> getViolations() {
        return violations;
    }
}
