package io.github.mahfaas.fraudshield.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested fraud-detection rule does not exist or is not registered
 * in the {@link io.github.mahfaas.fraudshield.engine.RuleEngine}.
 *
 * <p>Maps to HTTP {@code 404 Not Found}.
 */
public class RuleNotFoundException extends FraudShieldException {

    /**
     * @param ruleName the name of the rule that was not found
     */
    public RuleNotFoundException(String ruleName) {
        super("Fraud rule not found or not enabled: '" + ruleName + "'", HttpStatus.NOT_FOUND);
    }
}
