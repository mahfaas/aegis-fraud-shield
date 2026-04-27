package io.github.mahfaas.fraudshield.engine.rules;

import io.github.mahfaas.fraudshield.engine.Rule;
import io.github.mahfaas.fraudshield.engine.RuleResult;
import io.github.mahfaas.fraudshield.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flags or declines transactions based on high-risk merchant categories.
 * <p>
 * Categories in the "decline" set are immediately blocked.
 * Categories in the "review" set are sent to MANUAL_REVIEW.
 * Both sets are mutable at runtime via the REST API.
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fraud.rules.merchant-category.enabled", havingValue = "true", matchIfMissing = false)
public class MerchantCategoryRule implements Rule {

    private static final String RULE_NAME = "MERCHANT_CATEGORY";

    /** Categories that trigger an immediate DECLINE. */
    private final Set<String> declineCategories = ConcurrentHashMap.newKeySet();

    /** Categories that trigger a MANUAL_REVIEW flag. */
    private final Set<String> reviewCategories = ConcurrentHashMap.newKeySet();

    public MerchantCategoryRule() {
        // Well-known high-risk MCC categories pre-loaded as defaults
        declineCategories.addAll(Set.of("darkweb", "illegal"));
        reviewCategories.addAll(Set.of("gambling", "crypto", "adult", "firearms", "wire_transfer"));
        log.info("MerchantCategoryRule initialized: declineCategories={}, reviewCategories={}",
                declineCategories, reviewCategories);
    }

    @Override
    public RuleResult evaluate(Transaction transaction) {
        String category = transaction.getMerchantCategory();

        if (category == null || category.isBlank()) {
            return RuleResult.approve(RULE_NAME);
        }

        String normalised = category.trim().toLowerCase();

        if (declineCategories.contains(normalised)) {
            return RuleResult.decline(RULE_NAME,
                    "Merchant category '" + category + "' is prohibited");
        }

        if (reviewCategories.contains(normalised)) {
            return RuleResult.manualReview(RULE_NAME,
                    "Merchant category '" + category + "' requires manual review");
        }

        return RuleResult.approve(RULE_NAME);
    }

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public int getOrder() {
        return 15; // after BlacklistRule(10), before AmountAnomalyRule(20)
    }

    // ── Mutators (called by REST API) ──────────────────────────────────────────

    public void addDeclineCategory(String category) {
        declineCategories.add(category.trim().toLowerCase());
        log.info("Added '{}' to decline categories", category);
    }

    public void removeDeclineCategory(String category) {
        declineCategories.remove(category.trim().toLowerCase());
        log.info("Removed '{}' from decline categories", category);
    }

    public void addReviewCategory(String category) {
        reviewCategories.add(category.trim().toLowerCase());
        log.info("Added '{}' to review categories", category);
    }

    public void removeReviewCategory(String category) {
        reviewCategories.remove(category.trim().toLowerCase());
        log.info("Removed '{}' from review categories", category);
    }

    public Set<String> getDeclineCategories() {
        return Set.copyOf(declineCategories);
    }

    public Set<String> getReviewCategories() {
        return Set.copyOf(reviewCategories);
    }
}
