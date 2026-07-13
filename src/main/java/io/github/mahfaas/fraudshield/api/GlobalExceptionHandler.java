package io.github.mahfaas.fraudshield.api;

import io.github.mahfaas.fraudshield.exception.FraudShieldException;
import io.github.mahfaas.fraudshield.exception.TransactionValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Centralised exception → HTTP response mapping for the Fraud-Shield REST API.
 *
 * <p>Handler resolution order (most specific → least specific):
 * <ol>
 *   <li>{@link TransactionValidationException} — 422 with per-field violations list</li>
 *   <li>Any other {@link FraudShieldException} subtype — status from the exception itself</li>
 *   <li>{@link MethodArgumentTypeMismatchException} — 400 for bad path/query params</li>
 *   <li>{@link ResponseStatusException} — status taken from the exception itself
 *       (e.g. the 409s thrown by {@code FraudCaseService}'s state-machine checks);
 *       must be registered explicitly, otherwise the catch-all below would
 *       swallow it and downgrade it to a generic 500</li>
 *   <li>Catch-all {@link Exception} — 500</li>
 * </ol>
 *
 * <p>All responses use the structured {@link ErrorResponse} record so clients
 * always parse the same shape, regardless of which error occurred.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Domain exceptions ────────────────────────────────────────────────────

    /**
     * Handles validation failures that carry an individual violations list.
     * Responds with {@code 422 Unprocessable Entity}.
     */
    @ExceptionHandler(TransactionValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(TransactionValidationException ex) {
        log.warn("Transaction validation error: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.ofViolations(
                ex.getStatus().value(),
                ex.getStatus().getReasonPhrase(),
                ex.getMessage(),
                ex.getViolations()
        );
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    /**
     * Polymorphic handler for every other {@link FraudShieldException} subtype
     * ({@code BlacklistConflictException}, {@code RuleNotFoundException}, …).
     * The HTTP status is taken directly from the exception itself, so adding a
     * new subtype never requires touching this handler.
     */
    @ExceptionHandler(FraudShieldException.class)
    public ResponseEntity<ErrorResponse> handleDomain(FraudShieldException ex) {
        log.warn("Domain exception [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
                ex.getStatus().value(),
                ex.getStatus().getReasonPhrase(),
                ex.getMessage()
        );
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    // ── Spring / web exceptions ──────────────────────────────────────────────

    /**
     * Handles malformed path variable or query parameter types
     * (e.g. passing {@code "abc"} for a {@code BlacklistType} enum).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value '" + ex.getValue()
                + "' for parameter '" + ex.getName() + "'";
        log.warn("Type mismatch: {}", message);
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message
        );
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles {@link ResponseStatusException}, used for ad hoc HTTP errors that
     * don't warrant a dedicated {@link FraudShieldException} subtype (e.g. the
     * case state-machine 409 Conflicts in {@code FraudCaseService}).
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        log.warn("Response status exception: {}", ex.getReason());
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        ErrorResponse body = ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                ex.getReason()
        );
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    // ── Catch-all ────────────────────────────────────────────────────────────

    /**
     * Last-resort handler. Logs the full stack trace and returns a generic
     * {@code 500} without leaking internal details to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected error occurred. Please try again later."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
