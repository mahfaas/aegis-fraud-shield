package io.github.mahfaas.fraudshield.audit;

import io.github.mahfaas.fraudshield.model.Verdict;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

/**
 * Request DTO for searching the audit log with multi-field filtering.
 *
 * <p>All fields are optional. Providing multiple fields results in an AND condition.
 */
public record AuditSearchRequest(
        String transactionId,
        String accountId,
        Verdict verdict,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate
) {}
