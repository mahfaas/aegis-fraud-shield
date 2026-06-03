package io.github.mahfaas.fraudshield.exception;

import io.github.mahfaas.fraudshield.blacklist.BlacklistType;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an attempt is made to add a blacklist entry that already exists.
 *
 * <p>Maps to HTTP {@code 409 Conflict} — the resource state prevents the operation,
 * but the request itself is well-formed.
 */
public class BlacklistConflictException extends FraudShieldException {

    /**
     * @param type  the blacklist type (IP or BIN)
     * @param value the duplicate value that triggered the conflict
     */
    public BlacklistConflictException(BlacklistType type, String value) {
        super(
                "Blacklist entry already exists: type=" + type + ", value=" + value,
                HttpStatus.CONFLICT
        );
    }
}
