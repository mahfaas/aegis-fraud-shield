package io.github.mahfaas.fraudshield.engine.rules;

import io.github.mahfaas.fraudshield.engine.Rule;
import io.github.mahfaas.fraudshield.engine.RuleResult;
import io.github.mahfaas.fraudshield.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Flags or blocks transactions that exceed a configurable amount threshold.
 * <p>
 * Transactions above {@code declineThreshold} are immediately DECLINED.
 * Transactions above {@code reviewThreshold} (but below decline) are sent to MANUAL_REVIEW.
 * </p>
 */
@Slf4j
@Component
public class AmountAnomalyRule implements Rule {

    private static final String RULE_NAME = "AMOUNT_ANOMALY";

    private final AtomicReference<BigDecimal> declineThreshold;
    private final AtomicReference<BigDecimal> reviewThreshold;

    public AmountAnomalyRule(
            @Value("${fraud.rules.amount.decline-threshold:500000}") BigDecimal declineThreshold,
            @Value("${fraud.rules.amount.review-threshold:100000}") BigDecimal reviewThreshold) {
        this.declineThreshold = new AtomicReference<>(declineThreshold);
        this.reviewThreshold = new AtomicReference<>(reviewThreshold);
        log.info("AmountAnomalyRule initialized: reviewThreshold={}, declineThreshold={}",
                reviewThreshold, declineThreshold);
    }

    @Override
    public RuleResult evaluate(Transaction transaction) {
        BigDecimal amount = transaction.getAmount();

        if (amount.compareTo(declineThreshold.get()) > 0) {
            return RuleResult.decline(RULE_NAME,
                    "Amount " + amount + " exceeds decline threshold " + declineThreshold.get());
        }

        if (amount.compareTo(reviewThreshold.get()) > 0) {
            return RuleResult.manualReview(RULE_NAME,
                    "Amount " + amount + " exceeds review threshold " + reviewThreshold.get());
        }

        return RuleResult.approve(RULE_NAME);
    }

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public int getOrder() {
        return 20; // run after blacklist
    }

    public void setDeclineThreshold(BigDecimal threshold) {
        this.declineThreshold.set(threshold);
        log.info("Updated decline threshold to {}", threshold);
    }

    public void setReviewThreshold(BigDecimal threshold) {
        this.reviewThreshold.set(threshold);
        log.info("Updated review threshold to {}", threshold);
    }

    public BigDecimal getDeclineThreshold() {
        return declineThreshold.get();
    }

    public BigDecimal getReviewThreshold() {
        return reviewThreshold.get();
    }
}
