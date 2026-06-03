package io.github.mahfaas.fraudshield.audit;

import io.github.mahfaas.fraudshield.model.Verdict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for {@link AuditLogEntity}.
 *
 * <p>JPQL {@link Query} annotations are used instead of derived method names where
 * the query logic is non-trivial, keeping the intent explicit and the SQL readable.
 */
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    /**
     * Returns a page of audit entries for a specific account, newest first.
     *
     * @param accountId the account to query
     * @param pageable  page/sort descriptor
     * @return matching entries
     */
    Page<AuditLogEntity> findByAccountIdOrderByProcessedAtDesc(String accountId, Pageable pageable);

    /**
     * Returns a page of audit entries filtered by verdict.
     */
    Page<AuditLogEntity> findByVerdictOrderByProcessedAtDesc(Verdict verdict, Pageable pageable);

    /**
     * Returns the count of entries per verdict — used by the stats endpoint.
     *
     * <p>JPQL {@code GROUP BY} demonstrates SQL aggregation knowledge at the
     * repository layer without writing native SQL.
     *
     * @return list of {@code [verdict, count]} object arrays
     */
    @Query("SELECT a.verdict, COUNT(a) FROM AuditLogEntity a GROUP BY a.verdict")
    List<Object[]> countByVerdict();

    /**
     * Returns recent entries processed after the given instant.
     *
     * @param since     lower bound (exclusive)
     * @param pageable  page descriptor
     * @return matching entries, newest first
     */
    @Query("SELECT a FROM AuditLogEntity a WHERE a.processedAt > :since ORDER BY a.processedAt DESC")
    Page<AuditLogEntity> findRecentEntries(@Param("since") Instant since, Pageable pageable);

    /**
     * Returns the total risk exposure for a time window: sum of amounts on DECLINED transactions.
     *
     * <p>Demonstrates JPQL with aggregate function and WHERE clause.
     *
     * @param since lower bound (exclusive)
     * @return total declined amount, or {@code null} if no records exist
     */
    @Query("SELECT SUM(a.amount) FROM AuditLogEntity a " +
           "WHERE a.verdict = 'DECLINED' AND a.processedAt > :since")
    java.math.BigDecimal sumDeclinedAmountSince(@Param("since") Instant since);
}
