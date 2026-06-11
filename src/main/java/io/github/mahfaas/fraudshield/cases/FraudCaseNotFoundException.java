package io.github.mahfaas.fraudshield.cases;

import io.github.mahfaas.fraudshield.exception.FraudShieldException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested {@link FraudCase} cannot be found by its ID.
 *
 * <p>Handled by {@link io.github.mahfaas.fraudshield.api.GlobalExceptionHandler}
 * — polymorphically via the {@link io.github.mahfaas.fraudshield.exception.FraudShieldException}
 * handler, returning {@code 404 Not Found}.
 */
public class FraudCaseNotFoundException extends FraudShieldException {

    public FraudCaseNotFoundException(Long id) {
        super("Fraud case not found: id=" + id, HttpStatus.NOT_FOUND);
    }
}
