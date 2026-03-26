package io.github.mahfaas.fraudshield.engine;

import io.github.mahfaas.fraudshield.metrics.FraudMetrics;
import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.Verdict;
import io.github.mahfaas.fraudshield.model.VerdictedTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates the Chain of Responsibility pattern.
 * <p>
 * All registered {@link Rule} beans are executed in order.
 * The chain short-circuits on the first DECLINED result.
 * If any rule returns MANUAL_REVIEW (but none DECLINED), the final verdict is MANUAL_REVIEW.
 * Otherwise the transaction is APPROVED.
 * </p>
 */
@Slf4j
@Component
public class RuleEngine {

    private final List<Rule> rules;
    private final FraudMetrics metrics;

    /**
     * Spring auto-injects all beans implementing {@link Rule}, sorted by {@link Rule#getOrder()}.
     */
    public RuleEngine(List<Rule> rules, FraudMetrics metrics) {
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(Rule::getOrder))
                .toList();
        this.metrics = metrics;
        log.info("RuleEngine initialized with {} rules: {}", rules.size(),
                rules.stream().map(Rule::getName).toList());
    }

    /**
     * Evaluate a transaction against all rules.
     *
     * @param transaction the incoming transaction
     * @return a fully verdicted transaction with aggregated risk score
     */
    public VerdictedTransaction evaluate(Transaction transaction) {
        List<String> reasons = new ArrayList<>();
        Verdict finalVerdict = Verdict.APPROVED;
        int totalRiskScore = 0;

        for (Rule rule : rules) {
            RuleResult result = rule.evaluate(transaction);
            totalRiskScore += result.getRiskScore();

            if (result.isTriggered()) {
                metrics.recordRuleTriggered();
                reasons.add("[" + rule.getName() + "] " + result.getReason());
            }

            if (result.getVerdict() == Verdict.DECLINED) {
                log.info("Transaction {} DECLINED by rule [{}]: {}",
                        transaction.getTransactionId(), rule.getName(), result.getReason());
                finalVerdict = Verdict.DECLINED;
                break;
            }

            if (result.getVerdict() == Verdict.MANUAL_REVIEW) {
                log.info("Transaction {} flagged for MANUAL_REVIEW by rule [{}]: {}",
                        transaction.getTransactionId(), rule.getName(), result.getReason());
                finalVerdict = Verdict.MANUAL_REVIEW;
            }
        }

        return VerdictedTransaction.builder()
                .transaction(transaction)
                .verdict(finalVerdict)
                .reasons(reasons)
                .totalRiskScore(totalRiskScore)
                .processedAt(Instant.now())
                .build();
    }
}
