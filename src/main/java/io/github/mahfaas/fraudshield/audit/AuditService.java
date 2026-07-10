package io.github.mahfaas.fraudshield.audit;

import io.github.mahfaas.fraudshield.cases.FraudCaseService;
import io.github.mahfaas.fraudshield.model.Verdict;
import io.github.mahfaas.fraudshield.model.VerdictedTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Business logic for the audit log subsystem.
 *
 * <h3>Functional-programming showcase</h3>
 * <ul>
 *   <li>{@link Predicate} — reusable filter conditions composed with {@code and()}/{@code or()}</li>
 *   <li>{@link Function} — entity-to-DTO mapping passed as a method reference</li>
 *   <li>Stream pipelines — {@code filter}, {@code map}, {@code collect}, {@code groupingBy},
 *       {@code counting}, {@code toUnmodifiableList}</li>
 * </ul>
 */
@Slf4j
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final FraudCaseService fraudCaseService;

    /**
     * @param fraudCaseService injected lazily to avoid any potential
     *                         circular dependency through the Spring context.
     */
    public AuditService(AuditLogRepository repository,
                        @Lazy FraudCaseService fraudCaseService) {
        this.repository = repository;
        this.fraudCaseService = fraudCaseService;
    }

    // ── Reusable Predicates ──────────────────────────────────────────────────

    /** Keeps only DECLINED entries. */
    private static final Predicate<AuditLogEntity> IS_DECLINED =
            entry -> entry.getVerdict() == Verdict.DECLINED;

    /** Keeps only entries that have at least one reason recorded. */
    private static final Predicate<AuditLogEntity> HAS_REASONS =
            entry -> entry.getReasons() != null && !entry.getReasons().isBlank();

    /** Keeps only high-risk declined entries that also have a recorded reason. */
    private static final Predicate<AuditLogEntity> IS_ACTIONABLE =
            IS_DECLINED.and(HAS_REASONS);

    // ── Entity → DTO Function ────────────────────────────────────────────────

    /**
     * Reusable mapping {@link Function} from entity to summary DTO.
     * Passed as a method reference in stream pipelines below.
     */
    private static final Function<AuditLogEntity, AuditLogSummary> TO_SUMMARY =
            AuditLogSummary::from;

    // ── Write ────────────────────────────────────────────────────────────────

    /**
     * Persists a single verdicted transaction to the audit log.
     *
     * <p>Called by {@code TransactionConsumer} after every successful Rule Engine evaluation.
     * The reasons list is joined with {@code |} as a compact single-column representation.
     *
     * @param verdictedTransaction the result produced by the Rule Engine
     * @return the persisted audit entry
     */
    @Transactional
    public AuditLogEntity record(VerdictedTransaction verdictedTransaction) {
        String reasonsText = verdictedTransaction.getReasons().stream()
                .collect(Collectors.joining("|"));

        AuditLogEntity entry = AuditLogEntity.builder()
                .transactionId(verdictedTransaction.getTransaction().getTransactionId())
                .accountId(verdictedTransaction.getTransaction().getAccountId())
                .amount(verdictedTransaction.getTransaction().getAmount())
                .verdict(verdictedTransaction.getVerdict())
                .reasons(reasonsText)
                .processedAt(verdictedTransaction.getProcessedAt())
                .build();

        AuditLogEntity saved = repository.save(entry);
        log.debug("Audit entry saved: id={}, txId={}, verdict={}",
                saved.getId(), saved.getTransactionId(), saved.getVerdict());

        // Auto-create a fraud case for every MANUAL_REVIEW verdict.
        // The call is idempotent — duplicate messages are silently ignored.
        if (verdictedTransaction.getVerdict() == Verdict.MANUAL_REVIEW) {
            fraudCaseService.createFromVerdict(verdictedTransaction);
        }

        return saved;
    }

    // ── Read: paginated list ─────────────────────────────────────────────────

    /**
     * Returns a page of all audit entries, newest first.
     *
     * <p>The page of entities is projected to {@link AuditLogSummary} using a stream
     * {@code map} with the {@link #TO_SUMMARY} function reference, then collected into
     * an unmodifiable list.
     *
     * @param page zero-based page index
     * @param size items per page
     * @return paginated summaries
     */
    @Transactional(readOnly = true)
    public io.github.mahfaas.fraudshield.api.PagedResponse<AuditLogSummary> getPage(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("processedAt").descending());
        Page<AuditLogEntity> entityPage = repository.findAll(pageable);

        List<AuditLogSummary> summaries = entityPage.getContent()
                .stream()
                .map(TO_SUMMARY)                          // Function<Entity, DTO> as method ref
                .collect(Collectors.toUnmodifiableList());

        return io.github.mahfaas.fraudshield.api.PagedResponse.of(
                summaries, page, size, entityPage.getTotalElements());
    }

    /**
     * Returns a page of audit entries filtered by verdict.
     *
     * @param verdict the verdict to filter by
     * @param page    zero-based page index
     * @param size    items per page
     * @return paginated summaries for the given verdict
     */
    @Transactional(readOnly = true)
    public io.github.mahfaas.fraudshield.api.PagedResponse<AuditLogSummary> getPageByVerdict(
            Verdict verdict, int page, int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("processedAt").descending());
        Page<AuditLogEntity> entityPage =
                repository.findByVerdictOrderByProcessedAtDesc(verdict, pageable);

        List<AuditLogSummary> summaries = entityPage.getContent()
                .stream()
                .map(TO_SUMMARY)
                .collect(Collectors.toUnmodifiableList());

        return io.github.mahfaas.fraudshield.api.PagedResponse.of(
                summaries, page, size, entityPage.getTotalElements());
    }

    /**
     * Executes a dynamic search using Spring Data JPA Specifications.
     * Maps the resulting entities to summary DTOs.
     */
    @Transactional(readOnly = true)
    public io.github.mahfaas.fraudshield.api.PagedResponse<AuditLogSummary> search(AuditSearchRequest request, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "processedAt"));
        Specification<AuditLogEntity> spec = AuditLogSpecification.fromRequest(request);

        Page<AuditLogEntity> result = repository.findAll(spec, pageable);

        return io.github.mahfaas.fraudshield.api.PagedResponse.of(
                result.getContent().stream().map(TO_SUMMARY).collect(Collectors.toList()),
                page, size, result.getTotalElements()
        );
    }

    // ── Read: stats ──────────────────────────────────────────────────────────

    /**
     * Returns a verdict breakdown map using the JPQL GROUP BY query.
     *
     * <p>The raw {@code Object[]} result from JPQL is transformed into a typed
     * {@link Map} using a stream {@code collect(toMap(...))} pipeline.
     *
     * @return map of {@code Verdict → count}
     */
    @Transactional(readOnly = true)
    public Map<Verdict, Long> getVerdictStats() {
        List<Object[]> rows = repository.countByVerdict();

        // Stream over raw JPQL result rows and collect into an EnumMap for O(1) lookup
        return rows.stream()
                .collect(Collectors.toMap(
                        row -> (Verdict) row[0],    // key extractor
                        row -> (Long)   row[1],     // value extractor
                        (a, b) -> a,                // merge function (no duplicates expected)
                        () -> new EnumMap<>(Verdict.class)
                ));
    }

    /**
     * Returns a comprehensive stats snapshot for the dashboard endpoint.
     *
     * <p>Demonstrates:
     * <ul>
     *   <li>{@code Collectors.groupingBy + counting} on an in-memory list</li>
     *   <li>Predicate composition with {@code .and()}</li>
     *   <li>Chained stream operations</li>
     * </ul>
     *
     * @return summary stats for the last 24 hours
     */
    @Transactional(readOnly = true)
    public AuditStats getLast24hStats() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        PageRequest all = PageRequest.of(0, Integer.MAX_VALUE);
        List<AuditLogEntity> recent = repository.findRecentEntries(since, all).getContent();

        // Collectors.groupingBy + counting — explicit functional showcase
        Map<Verdict, Long> byVerdict = recent.stream()
                .collect(Collectors.groupingBy(AuditLogEntity::getVerdict, Collectors.counting()));

        // Predicate composition: IS_DECLINED.and(HAS_REASONS)
        long actionableDeclines = recent.stream()
                .filter(IS_ACTIONABLE)
                .count();

        // Sum amounts of declined transactions using stream reduce
        BigDecimal declinedAmount = recent.stream()
                .filter(IS_DECLINED)
                .map(AuditLogEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Collect unique rule names from reason strings using flatMap
        List<String> topRules = recent.stream()
                .filter(HAS_REASONS)
                .flatMap(e -> Arrays.stream(e.getReasons().split("\\|")))
                .map(reason -> reason.replaceAll("\\[(.+?)\\].*", "$1").trim())
                .filter(Predicate.not(String::isBlank))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return new AuditStats(
                (long) recent.size(),
                byVerdict.getOrDefault(Verdict.APPROVED,     0L),
                byVerdict.getOrDefault(Verdict.DECLINED,     0L),
                byVerdict.getOrDefault(Verdict.MANUAL_REVIEW, 0L),
                actionableDeclines,
                declinedAmount,
                topRules,
                since
        );
    }

    /**
     * Returns a per-day verdict breakdown for the last {@code days} days (inclusive of today).
     *
     * <p>Backed by a single native SQL query ({@link AuditLogRepository#dailyVerdictBreakdown})
     * that pre-pivots verdict counts per day using Postgres {@code FILTER (WHERE ...)}, so no
     * further grouping is needed here — just row-to-DTO mapping.
     *
     * @param days lookback window in days
     * @return one {@link DailyVerdictStats} entry per day that had activity, oldest first
     */
    @Transactional(readOnly = true)
    public List<DailyVerdictStats> getDailyVerdictBreakdown(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> rows = repository.dailyVerdictBreakdown(since);

        List<DailyVerdictStats> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            result.add(new DailyVerdictStats(
                    ((java.sql.Date) row[0]).toLocalDate(),
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue(),
                    ((Number) row[3]).longValue()
            ));
        }
        return result;
    }

    /**
     * Returns the top accounts by DECLINED count/amount over the last {@code days} days.
     *
     * @param days  lookback window in days
     * @param limit maximum number of accounts to return
     * @return accounts ordered by declined amount, highest first
     */
    @Transactional(readOnly = true)
    public List<AccountRiskSummary> getTopDecliningAccounts(int days, int limit) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> rows = repository.topDecliningAccounts(since, limit);

        List<AccountRiskSummary> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            result.add(new AccountRiskSummary(
                    (String) row[0],
                    ((Number) row[1]).longValue(),
                    (BigDecimal) row[2]
            ));
        }
        return result;
    }

    // ── Stats records ────────────────────────────────────────────────────────

    /**
     * Immutable stats snapshot for the last 24-hour window.
     */
    public record AuditStats(
            long totalProcessed,
            long approved,
            long declined,
            long manualReview,
            long actionableDeclines,
            BigDecimal declinedAmount,
            List<String> topTriggeredRules,
            Instant windowStart
    ) {}

    /**
     * Verdict counts for a single calendar day, used by the daily trend endpoint.
     */
    public record DailyVerdictStats(
            LocalDate date,
            long approved,
            long declined,
            long manualReview
    ) {}

    /**
     * Decline exposure for a single account, used by the top-accounts endpoint.
     */
    public record AccountRiskSummary(
            String accountId,
            long declineCount,
            BigDecimal declineAmount
    ) {}
}
