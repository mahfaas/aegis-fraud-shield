package io.github.mahfaas.fraudshield.api;

import io.github.mahfaas.fraudshield.engine.rules.AmountAnomalyRule;
import io.github.mahfaas.fraudshield.engine.rules.VelocityRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * REST API for dynamic rule configuration.
 */
@RestController
@RequestMapping("/api/v1/rules/config")
@RequiredArgsConstructor
@Tag(name = "Rule Configuration", description = "View and update fraud-detection rule thresholds at runtime")
public class RuleConfigController {

    private final AmountAnomalyRule amountAnomalyRule;
    private final VelocityRule velocityRule;

    @GetMapping
    @Operation(summary = "Get current configuration of all rules")
    public Map<String, Object> getConfig() {
        return Map.of(
                "AMOUNT_ANOMALY", Map.of(
                        "declineThreshold", amountAnomalyRule.getDeclineThreshold(),
                        "reviewThreshold", amountAnomalyRule.getReviewThreshold()
                ),
                "VELOCITY", Map.of(
                        "maxTransactions", velocityRule.getMaxTransactions(),
                        "windowSeconds", velocityRule.getWindowSeconds()
                )
        );
    }

    @PutMapping("/amount-anomaly")
    @Operation(summary = "Update AmountAnomalyRule thresholds")
    public Map<String, BigDecimal> updateAmountConfig(@RequestBody AmountConfigRequest request) {
        if (request.declineThreshold() != null) {
            amountAnomalyRule.setDeclineThreshold(request.declineThreshold());
        }
        if (request.reviewThreshold() != null) {
            amountAnomalyRule.setReviewThreshold(request.reviewThreshold());
        }
        return Map.of(
                "declineThreshold", amountAnomalyRule.getDeclineThreshold(),
                "reviewThreshold", amountAnomalyRule.getReviewThreshold()
        );
    }

    @PutMapping("/velocity")
    @Operation(summary = "Update VelocityRule thresholds")
    public Map<String, Object> updateVelocityConfig(@RequestBody VelocityConfigRequest request) {
        if (request.maxTransactions() != null) {
            velocityRule.setMaxTransactions(request.maxTransactions());
        }
        if (request.windowSeconds() != null) {
            velocityRule.setWindowSeconds(request.windowSeconds());
        }
        return Map.of(
                "maxTransactions", velocityRule.getMaxTransactions(),
                "windowSeconds", velocityRule.getWindowSeconds()
        );
    }

    public record AmountConfigRequest(BigDecimal declineThreshold, BigDecimal reviewThreshold) {}
    public record VelocityConfigRequest(Integer maxTransactions, Long windowSeconds) {}
}
