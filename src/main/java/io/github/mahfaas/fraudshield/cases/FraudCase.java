package io.github.mahfaas.fraudshield.cases;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity representing an open fraud investigation case.
 *
 * <p>A case is automatically created whenever the Rule Engine produces a
 * {@code MANUAL_REVIEW} verdict. It tracks the full lifecycle of the
 * analyst's investigation — from creation through to a final determination.
 *
 * <h3>Table: {@code fraud_case}</h3>
 * <pre>
 *   id                BIGSERIAL  PK
 *   transaction_id    VARCHAR    NOT NULL UNIQUE  (the original txId)
 *   account_id        VARCHAR    NOT NULL
 *   amount            NUMERIC
 *   risk_score        INT
 *   triggered_rules   TEXT       (pipe-delimited rule names)
 *   status            VARCHAR    NOT NULL DEFAULT 'OPEN'
 *   analyst_notes     TEXT       (free-text, updated by analysts)
 *   assigned_to       VARCHAR    (free-text analyst identifier, nullable)
 *   created_at        TIMESTAMP  NOT NULL
 *   updated_at        TIMESTAMP  NOT NULL
 * </pre>
 */
@Entity
@Table(name = "fraud_case")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The original transaction ID from the payment system.
     * Unique constraint ensures one case per transaction — idempotent creation.
     */
    @Column(name = "transaction_id", nullable = false, unique = true, length = 36)
    private String transactionId;

    /** Account that originated the suspicious transaction. */
    @Column(name = "account_id", nullable = false, length = 50)
    private String accountId;

    /** Transaction amount for quick analyst context. */
    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    /** Composite risk score at the time the verdict was produced. */
    @Column(name = "risk_score")
    private Integer riskScore;

    /**
     * Pipe-delimited list of rule names that were triggered.
     * Example: {@code "GEO_VELOCITY|TIME_WINDOW"}
     */
    @Column(name = "triggered_rules", columnDefinition = "TEXT")
    private String triggeredRules;

    /**
     * Current lifecycle status of the case.
     * Stored as VARCHAR for human-readability in DB queries.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private FraudCaseStatus status = FraudCaseStatus.OPEN;

    /**
     * Free-text notes added by the analyst during investigation.
     * Updated via {@code PUT /api/v1/cases/{id}/status}.
     */
    @Column(name = "analyst_notes", columnDefinition = "TEXT")
    private String analystNotes;

    /**
     * Free-text identifier of the analyst currently working the case.
     * Null means unassigned. No auth system exists, so this is not a
     * foreign key to a user table — same rationale as {@link FraudCaseNote#getAuthor()}.
     */
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    /** UTC instant when the case was auto-created by {@link io.github.mahfaas.fraudshield.audit.AuditService}. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** UTC instant of the most recent status update. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void onPrePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onPreUpdate() {
        this.updatedAt = Instant.now();
    }
}
