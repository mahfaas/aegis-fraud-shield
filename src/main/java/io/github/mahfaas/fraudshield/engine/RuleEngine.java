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

@Slf4j
@Component
public class RuleEngine {

    private final List<Rule> rules;
    private final FraudMetrics metrics;

    public RuleEngine(List<Rule> rules, FraudMetrics metrics) {
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(Rule::getOrder))
                .toList();
        this.metrics = metrics;
        log.info("RuleEngine initialized with {} rules: {}", rules.size(),
                rules.stream().map(Rule::getName).toList());
    }

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
