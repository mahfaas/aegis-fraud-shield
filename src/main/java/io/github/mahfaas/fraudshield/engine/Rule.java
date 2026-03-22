package io.github.mahfaas.fraudshield.engine;

import io.github.mahfaas.fraudshield.model.Transaction;

/**
 * Contract for a single fraud-detection rule.
 * <p>
 * Rules are executed in a chain (Chain of Responsibility pattern).
 * Each rule independently evaluates a transaction and returns a {@link RuleResult}.
 * </p>
 */
public interface Rule {

    /**
     * Evaluate the given transaction against this rule.
     *
     * @param transaction the transaction to evaluate
     * @return the result of the evaluation
     */
    RuleResult evaluate(Transaction transaction);

    /**
     * @return a short name identifying this rule (used in logging and metrics).
     */
    String getName();

    /**
     * @return execution order — lower values run first.
     */
    default int getOrder() {
        return 0;
    }
}
