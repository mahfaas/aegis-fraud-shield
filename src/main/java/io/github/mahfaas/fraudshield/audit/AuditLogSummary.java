package io.github.mahfaas.fraudshield.audit;

import io.github.mahfaas.fraudshield.model.Verdict;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Read-only summary DTO projected from {@link AuditLogEntity}.
 *
 * <p>Returned by {@code GET /api/v1/audit} inside a {@code PagedResponse<AuditLogSummary>}.
 * Using a separate DTO (rather than exposing the entity directly) keeps the API
 * contract stable if the schema evolves, and avoids lazy-loading surprises in JSON
 * serialisation.
 *
 * <p>The reasons string is split back into a {@code List<String>} here so consumers
 * receive a properly typed array rather than a raw pipe-delimited string.
 */
@Schema(description = "Audit log summary for a single processed transaction")
public record AuditLogSummary(

        @Schema(description = "Audit log entry ID")
        Long id,

        @Schema(description = "Original transaction ID")
        String transactionId,

        @Schema(description = "Account ID")
        String accountId,

        @Schema(description = "Transaction amount")
        BigDecimal amount,

        @Schema(description = "Final verdict of the Rule Engine")
        Verdict verdict,

        @Schema(description = "Reasons reported by triggered rules")
        List<String> reasons,

        @Schema(description = "UTC timestamp when the verdict was produced")
        Instant processedAt
) {
    /**
     * Maps an {@link AuditLogEntity} to this summary, splitting the stored
     * reasons string using a stream pipeline.
     *
     * @param entity the source entity
     * @return the projected summary
     */
    public static AuditLogSummary from(AuditLogEntity entity) {
        List<String> reasonList = (entity.getReasons() == null || entity.getReasons().isBlank())
                ? List.of()
                : List.of(entity.getReasons().split("\\|"));

        return new AuditLogSummary(
                entity.getId(),
                entity.getTransactionId(),
                entity.getAccountId(),
                entity.getAmount(),
                entity.getVerdict(),
                reasonList,
                entity.getProcessedAt()
        );
    }
}
