package io.github.mahfaas.fraudshield.api;

import io.github.mahfaas.fraudshield.audit.AuditLogSummary;
import io.github.mahfaas.fraudshield.audit.AuditSearchRequest;
import io.github.mahfaas.fraudshield.audit.AuditService;
import io.github.mahfaas.fraudshield.model.Verdict;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API exposing the audit log of all processed transactions.
 *
 * <p>All list endpoints return a {@link PagedResponse}{@code <AuditLogSummary>} — the
 * generic wrapper enforces a consistent pagination envelope across the entire API.
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit Log", description = "Query the fraud-decision history for all processed transactions")
public class AuditController {

    private final AuditService auditService;

    /**
     * Returns a paginated list of all audit entries, newest first.
     *
     * <p>The {@code <AuditLogSummary>} type argument demonstrates that {@link PagedResponse}
     * is a genuine generic type — not a raw type or {@code Object} wrapper.
     *
     * @param page zero-based page index (default 0)
     * @param size items per page (default 20, max 100)
     */
    @GetMapping
    @Operation(
            summary = "List all audit entries",
            description = "Returns a paginated audit log of every transaction processed by the Rule Engine, newest first."
    )
    public PagedResponse<AuditLogSummary> getAll(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0")  int page,
            @Parameter(description = "Page size (max 100)")   @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(size, 100);
        return auditService.getPage(page, safeSize);
    }

    /**
     * Returns audit entries filtered by a specific verdict.
     *
     * @param verdict one of {@code APPROVED}, {@code DECLINED}, {@code MANUAL_REVIEW}
     * @param page    zero-based page index
     * @param size    items per page (max 100)
     */
    @GetMapping("/by-verdict/{verdict}")
    @Operation(
            summary = "List audit entries by verdict",
            description = "Returns paginated audit entries filtered by APPROVED, DECLINED, or MANUAL_REVIEW."
    )
    public PagedResponse<AuditLogSummary> getByVerdict(
            @PathVariable Verdict verdict,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(size, 100);
        return auditService.getPageByVerdict(verdict, page, safeSize);
    }

    /**
     * Executes a dynamic search using query parameters mapped to {@link AuditSearchRequest}.
     * All parameters are optional. Example:
     * {@code GET /api/v1/audit/search?verdict=DECLINED&startDate=2026-06-01T00:00:00Z}
     */
    @GetMapping("/search")
    @Operation(summary = "Search audit log", description = "Multi-field dynamic search. All params are optional.")
    public PagedResponse<AuditLogSummary> search(
            @ModelAttribute AuditSearchRequest searchRequest,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return auditService.search(searchRequest, page, Math.min(size, 100));
    }

    /**
     * Returns a per-verdict count breakdown using the JPQL GROUP BY query.
     *
     * @return map of {@code "APPROVED" → 8800, "DECLINED" → 1100, …}
     */
    @GetMapping("/stats/verdict-counts")
    @Operation(
            summary = "Verdict count breakdown",
            description = "Returns total transaction counts grouped by verdict, sourced from a JPQL GROUP BY query."
    )
    public Map<Verdict, Long> getVerdictCounts() {
        return auditService.getVerdictStats();
    }

    /**
     * Returns a rich stats snapshot for the last 24 hours.
     *
     * <p>The underlying {@link AuditService#getLast24hStats()} method uses
     * {@code Collectors.groupingBy}, predicate composition, stream {@code flatMap},
     * and {@code reduce} — making this endpoint a live demonstration of the
     * functional-programming skill set.
     */
    @GetMapping("/stats/last-24h")
    @Operation(
            summary = "Last-24h audit stats",
            description = "Returns a rich stats snapshot: totals by verdict, actionable declines, " +
                          "total declined amount, and top triggered rules — all derived via stream pipelines."
    )
    public AuditService.AuditStats getLast24hStats() {
        return auditService.getLast24hStats();
    }

    /**
     * Returns a per-day verdict breakdown for the last {@code days} days.
     *
     * @param days lookback window in days (default 7)
     */
    @GetMapping("/stats/daily")
    @Operation(
            summary = "Daily verdict trend",
            description = "Returns one row per day with APPROVED/DECLINED/MANUAL_REVIEW counts, " +
                          "sourced from a native SQL query using date_trunc and FILTER (WHERE ...)."
    )
    public java.util.List<AuditService.DailyVerdictStats> getDailyStats(
            @Parameter(description = "Lookback window in days") @RequestParam(defaultValue = "7") int days) {
        return auditService.getDailyVerdictBreakdown(days);
    }

    /**
     * Returns the top accounts by DECLINED count/amount over the last {@code days} days.
     *
     * @param days  lookback window in days (default 30)
     * @param limit maximum number of accounts to return (default 10, max 100)
     */
    @GetMapping("/stats/top-accounts")
    @Operation(
            summary = "Top declining accounts",
            description = "Returns the accounts with the highest DECLINED exposure over the given window, " +
                          "ordered by total declined amount."
    )
    public java.util.List<AuditService.AccountRiskSummary> getTopDecliningAccounts(
            @Parameter(description = "Lookback window in days") @RequestParam(defaultValue = "30") int days,
            @Parameter(description = "Max accounts to return") @RequestParam(defaultValue = "10") int limit) {
        return auditService.getTopDecliningAccounts(days, Math.min(limit, 100));
    }

    /**
     * Returns rule trigger frequency over the last {@code days} days, ordered by frequency descending.
     *
     * @param days  lookback window in days (default 7)
     * @param limit maximum number of rules to return (default 10, max 100)
     */
    @GetMapping("/stats/rule-triggers")
    @Operation(
            summary = "Rule trigger frequency",
            description = "Returns rule trigger counts over an arbitrary window, ordered by frequency descending. " +
                          "Generalizes the top-5 list embedded in the last-24h snapshot to any window/limit."
    )
    public java.util.List<AuditService.RuleTriggerCount> getRuleTriggerFrequency(
            @Parameter(description = "Lookback window in days") @RequestParam(defaultValue = "7") int days,
            @Parameter(description = "Max rules to return") @RequestParam(defaultValue = "10") int limit) {
        return auditService.getRuleTriggerFrequency(days, Math.min(limit, 100));
    }

    /**
     * Returns transaction amount distribution stats over the last {@code days} days.
     *
     * @param days lookback window in days (default 30)
     */
    @GetMapping("/stats/amount-distribution")
    @Operation(
            summary = "Amount distribution",
            description = "Returns count/min/max/avg/p50/p95 of transaction amounts over the given window, " +
                          "sourced from a native SQL query using Postgres percentile_cont."
    )
    public AuditService.AmountDistributionStats getAmountDistribution(
            @Parameter(description = "Lookback window in days") @RequestParam(defaultValue = "30") int days) {
        return auditService.getAmountDistribution(days);
    }
}
