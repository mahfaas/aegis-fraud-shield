package io.github.mahfaas.fraudshield.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Standardised error payload returned by {@link GlobalExceptionHandler} for all
 * failed API requests.
 *
 * <p>Using a Java {@code record} makes the contract explicit and immutable:
 * every field is always present in the JSON response, making client-side error
 * handling predictable.
 *
 * <pre>
 * {
 *   "status":     409,
 *   "error":      "Conflict",
 *   "message":    "Blacklist entry already exists: type=IP, value=1.2.3.4",
 *   "violations": [],
 *   "timestamp":  "2025-06-03T09:00:00Z"
 * }
 * </pre>
 */
@Schema(description = "Standardised error response returned for all API failures")
public record ErrorResponse(

        @Schema(description = "HTTP status code", example = "409")
        int status,

        @Schema(description = "Short HTTP status description", example = "Conflict")
        String error,

        @Schema(description = "Human-readable error detail")
        String message,

        @Schema(description = "Field-level violation messages (populated for validation errors)")
        List<String> violations,

        @Schema(description = "Server-side UTC timestamp of the error")
        Instant timestamp
) {
    /**
     * Convenience factory for errors without field violations.
     */
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, List.of(), Instant.now());
    }

    /**
     * Convenience factory for validation errors that carry individual violations.
     */
    public static ErrorResponse ofViolations(int status, String error,
                                             String message, List<String> violations) {
        return new ErrorResponse(status, error, message, List.copyOf(violations), Instant.now());
    }
}
