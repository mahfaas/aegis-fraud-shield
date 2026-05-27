package io.github.mahfaas.fraudshield.api;

import io.github.mahfaas.fraudshield.engine.RuleEngine;
import io.github.mahfaas.fraudshield.metrics.FraudMetrics;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST endpoint providing a live operational snapshot of the fraud engine.
 * <p>
 * Complements the full Prometheus/Grafana stack with a simple human-readable
 * summary that can be polled by health checks, dashboards, or on-call scripts.
 * </p>
 *
 * <pre>
 * GET /api/v1/metrics/summary
 * {
 *   "received":     10542,
 *   "approved":      8800,
 *   "declined":      1100,
 *   "manualReview":   642,
 *   "dlq":              0,
 *   "activeRules":      4
 * }
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
@Tag(name = "Metrics", description = "Live operational snapshot of the fraud detection engine")
public class MetricsController {

    private final FraudMetrics fraudMetrics;
    private final RuleEngine ruleEngine;

    /**
     * Returns a live snapshot of key counters.
     * <p>
     * Values are sourced directly from in-process Micrometer counters —
     * no external call to Prometheus needed.
     * </p>
     */
    @GetMapping("/summary")
    @Operation(
            summary = "Get live metrics summary",
            description = "Returns current transaction counts by verdict, DLQ total, and the number of active fraud rules."
    )
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("received",     fraudMetrics.getReceivedCount());
        summary.put("approved",     fraudMetrics.getApprovedCount());
        summary.put("declined",     fraudMetrics.getDeclinedCount());
        summary.put("manualReview", fraudMetrics.getManualReviewCount());
        summary.put("dlq",          fraudMetrics.getDlqCount());
        summary.put("activeRules",  ruleEngine.getRuleCount());
        return summary;
    }
}
