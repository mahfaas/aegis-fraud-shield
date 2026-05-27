package io.github.mahfaas.fraudshield.api;

import io.github.mahfaas.fraudshield.engine.rules.AmountAnomalyRule;
import io.github.mahfaas.fraudshield.engine.rules.GeoVelocityRule;
import io.github.mahfaas.fraudshield.engine.rules.MerchantCategoryRule;
import io.github.mahfaas.fraudshield.engine.rules.VelocityRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private final Optional<GeoVelocityRule> geoVelocityRule;
    private final Optional<MerchantCategoryRule> merchantCategoryRule;

    @GetMapping
    @Operation(summary = "Get current configuration of all rules")
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("AMOUNT_ANOMALY", Map.of(
                "declineThreshold", amountAnomalyRule.getDeclineThreshold(),
                "reviewThreshold", amountAnomalyRule.getReviewThreshold()
        ));
        config.put("VELOCITY", Map.of(
                "maxTransactions", velocityRule.getMaxTransactions(),
                "windowSeconds", velocityRule.getWindowSeconds()
        ));
        geoVelocityRule.ifPresentOrElse(
                rule -> config.put("GEO_VELOCITY", Map.of(
                        "enabled", true,
                        "windowSeconds", GeoVelocityRule.WINDOW_SECONDS
                )),
                () -> config.put("GEO_VELOCITY", Map.of("enabled", false))
        );
        merchantCategoryRule.ifPresentOrElse(
                rule -> config.put("MERCHANT_CATEGORY", Map.of(
                        "enabled", true,
                        "declineCategories", rule.getDeclineCategories(),
                        "reviewCategories", rule.getReviewCategories()
                )),
                () -> config.put("MERCHANT_CATEGORY", Map.of("enabled", false))
        );
        return config;
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

    @PutMapping("/merchant-category/decline")
    @Operation(summary = "Add or remove categories from the MerchantCategoryRule decline list")
    public ResponseEntity<?> updateMerchantDeclineCategories(
            @RequestBody MerchantCategoryConfigRequest request) {
        return merchantCategoryRule.<ResponseEntity<?>>map(rule -> {
            request.add().forEach(rule::addDeclineCategory);
            request.remove().forEach(rule::removeDeclineCategory);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("declineCategories", rule.getDeclineCategories());
            body.put("reviewCategories",  rule.getReviewCategories());
            return ResponseEntity.ok(body);
        }).orElseGet(() -> ResponseEntity.status(404).body(
                Map.of("error", "MerchantCategoryRule is disabled. Set fraud.rules.merchant-category.enabled=true to activate.")));
    }

    @PutMapping("/merchant-category/review")
    @Operation(summary = "Add or remove categories from the MerchantCategoryRule review list")
    public ResponseEntity<?> updateMerchantReviewCategories(
            @RequestBody MerchantCategoryConfigRequest request) {
        return merchantCategoryRule.<ResponseEntity<?>>map(rule -> {
            request.add().forEach(rule::addReviewCategory);
            request.remove().forEach(rule::removeReviewCategory);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("declineCategories", rule.getDeclineCategories());
            body.put("reviewCategories",  rule.getReviewCategories());
            return ResponseEntity.ok(body);
        }).orElseGet(() -> ResponseEntity.status(404).body(
                Map.of("error", "MerchantCategoryRule is disabled. Set fraud.rules.merchant-category.enabled=true to activate.")));
    }

    // ── Request records ──────────────────────────────────────────────────────

    public record AmountConfigRequest(BigDecimal declineThreshold, BigDecimal reviewThreshold) {}

    public record VelocityConfigRequest(Integer maxTransactions, Long windowSeconds) {}

    /**
     * @param add    categories to add to the list (empty list = no-op)
     * @param remove categories to remove from the list (empty list = no-op)
     */
    public record MerchantCategoryConfigRequest(List<String> add, List<String> remove) {}
}
