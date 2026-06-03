package io.github.mahfaas.fraudshield.audit;

import io.github.mahfaas.fraudshield.model.Verdict;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity that maps the {@code audit_log} table created by Flyway migration V1.
 *
 * <p>Every transaction that passes through the Rule Engine is persisted here,
 * giving the system a complete, queryable history of all fraud decisions.
 *
 * <p>The table already exists (V1__init_schema.sql), so Hibernate only validates
 * the mapping — no DDL changes are needed.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Transaction ID from the originating payment system. */
    @Column(name = "transaction_id", nullable = false, length = 36)
    private String transactionId;

    /** Account that initiated the transaction. */
    @Column(name = "account_id", nullable = false, length = 50)
    private String accountId;

    /** Transaction amount — preserved for after-the-fact analytics. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /**
     * Final verdict of the Rule Engine.
     * Stored as a {@code VARCHAR} so the column is human-readable without joins.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Verdict verdict;

    /**
     * Pipe-delimited human-readable reasons from the triggered rules.
     * Stored as plain TEXT — no need for a separate join table for this read-mostly data.
     */
    @Column(columnDefinition = "TEXT")
    private String reasons;

    /** UTC instant when the Rule Engine produced this verdict. */
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    // ── Manual-review fields (populated by a future case-management workflow) ──

    @Column(name = "reviewed_by", length = 50)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_verdict", length = 20)
    private String reviewVerdict;
}
