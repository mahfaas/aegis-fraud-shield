package io.github.mahfaas.fraudshield.cases;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link FraudCase}.
 *
 * <p>Inherits standard CRUD from {@link JpaRepository}. The custom
 * {@link #countByStatus()} query demonstrates JPQL GROUP BY — the same
 * technique used in the audit log subsystem.
 */
public interface FraudCaseRepository extends JpaRepository<FraudCase, Long> {

    /** Find a case by its originating transaction ID. */
    Optional<FraudCase> findByTransactionId(String transactionId);

    /** Returns true if a case already exists for the given transaction ID. */
    boolean existsByTransactionId(String transactionId);

    /** Paginated query filtered by lifecycle status. */
    Page<FraudCase> findByStatusOrderByCreatedAtDesc(FraudCaseStatus status, Pageable pageable);

    /** Paginated query filtered by assignee. */
    Page<FraudCase> findByAssignedToOrderByCreatedAtDesc(String assignedTo, Pageable pageable);

    /** Paginated query filtered by triage priority. */
    Page<FraudCase> findByPriorityOrderByCreatedAtDesc(CasePriority priority, Pageable pageable);

    /**
     * JPQL GROUP BY query — returns pairs of [status, count].
     * Demonstrates the same raw-JPQL aggregation technique as
     * {@link io.github.mahfaas.fraudshield.audit.AuditLogRepository#countByVerdict()}.
     */
    @Query("SELECT c.status, COUNT(c) FROM FraudCase c GROUP BY c.status")
    java.util.List<Object[]> countByStatus();

    /**
     * JPQL GROUP BY query scoped to a lookback window — returns pairs of
     * [status, count] for cases created since the given instant. Backs the
     * review-accuracy report, which measures how MANUAL_REVIEW verdicts from
     * the Rule Engine were ultimately resolved by analysts.
     */
    @Query("SELECT c.status, COUNT(c) FROM FraudCase c WHERE c.createdAt >= :since GROUP BY c.status")
    java.util.List<Object[]> countByStatusSince(@Param("since") Instant since);
}
