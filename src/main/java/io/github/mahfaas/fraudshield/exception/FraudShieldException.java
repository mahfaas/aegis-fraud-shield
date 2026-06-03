package io.github.mahfaas.fraudshield.exception;

import org.springframework.http.HttpStatus;

/**
 * Abstract base for all domain-specific exceptions in Aegis Fraud-Shield.
 *
 * <p>Every concrete subclass must supply:
 * <ul>
 *   <li>a human-readable {@code message}</li>
 *   <li>an {@link HttpStatus} that the {@code GlobalExceptionHandler} will use
 *       when converting this exception into an HTTP response</li>
 * </ul>
 *
 * <p>Extending {@link RuntimeException} keeps the call sites clean — callers are
 * not forced to declare checked exceptions, yet the hierarchy is still fully typed
 * so handlers can catch specific subtypes with distinct HTTP semantics.
 */
public abstract class FraudShieldException extends RuntimeException {

    private final HttpStatus status;

    /**
     * @param message human-readable error detail (included in the API response)
     * @param status  HTTP status code the handler should return
     */
    protected FraudShieldException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    /**
     * @param message human-readable error detail
     * @param status  HTTP status code
     * @param cause   the original throwable that triggered this exception
     */
    protected FraudShieldException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /**
     * @return the HTTP status the {@code GlobalExceptionHandler} should respond with.
     */
    public HttpStatus getStatus() {
        return status;
    }
}
