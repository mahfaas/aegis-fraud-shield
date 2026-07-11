package io.github.mahfaas.fraudshield.audit;

import io.github.mahfaas.fraudshield.model.Verdict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long>, JpaSpecificationExecutor<AuditLogEntity> {

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

    /**
     * Returns a per-day verdict breakdown since the given instant, one row per day.
     *
     * <p>Native SQL is used (rather than JPQL) so that {@code date_trunc} and the
     * Postgres {@code FILTER (WHERE ...)} clause can pre-pivot verdict counts into
     * columns, avoiding a second grouping pass in Java.
     *
     * @return list of {@code [day (java.sql.Date), approved, declined, manualReview]} rows
     */
    @Query(value = """
            SELECT date_trunc('day', processed_at)::date AS day,
                   COUNT(*) FILTER (WHERE verdict = 'APPROVED')      AS approved,
                   COUNT(*) FILTER (WHERE verdict = 'DECLINED')      AS declined,
                   COUNT(*) FILTER (WHERE verdict = 'MANUAL_REVIEW') AS manual_review
            FROM audit_log
            WHERE processed_at > :since
            GROUP BY day
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> dailyVerdictBreakdown(@Param("since") Instant since);

    /**
     * Returns the top accounts by DECLINED count/amount since the given instant.
     *
     * @return list of {@code [accountId, declineCount, declineAmount]} rows, highest amount first
     */
    @Query(value = """
            SELECT account_id,
                   COUNT(*)                    AS decline_count,
                   COALESCE(SUM(amount), 0)    AS decline_amount
            FROM audit_log
            WHERE verdict = 'DECLINED' AND processed_at > :since
            GROUP BY account_id
            ORDER BY decline_amount DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> topDecliningAccounts(@Param("since") Instant since, @Param("limit") int limit);

    /**
     * Returns amount distribution stats (count, min, max, avg, p50, p95) since the given instant.
     *
     * <p>Native SQL is required for {@code percentile_cont}, a Postgres ordered-set aggregate
     * with no JPQL equivalent. Aggregate queries without {@code GROUP BY} always return exactly
     * one row, even when no transactions match the filter (count is 0, the rest are {@code NULL}).
     *
     * @return single-row list: {@code [count, min, max, avg, p50, p95]}
     */
    @Query(value = """
            SELECT COUNT(*)                                            AS cnt,
                   MIN(amount)                                         AS min_amount,
                   MAX(amount)                                         AS max_amount,
                   AVG(amount)                                         AS avg_amount,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY amount)  AS p50,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY amount) AS p95
            FROM audit_log
            WHERE processed_at > :since
            """, nativeQuery = true)
    List<Object[]> amountDistribution(@Param("since") Instant since);
}
