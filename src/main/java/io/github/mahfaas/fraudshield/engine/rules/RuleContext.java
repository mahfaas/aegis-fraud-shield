package io.github.mahfaas.fraudshield.engine.rules;

import io.github.mahfaas.fraudshield.model.Verdict;
import lombok.Getter;

/**
 * Thread-local carrier that passes the accumulated risk state from
 * {@link io.github.mahfaas.fraudshield.engine.RuleEngine} to
 * {@link RiskScoreThresholdRule}.
 *
 * <h3>Why a thread-local?</h3>
 * <p>
 * The {@link io.github.mahfaas.fraudshield.engine.Rule} interface is stateless by design —
 * each rule receives only the {@link io.github.mahfaas.fraudshield.model.Transaction} and
 * returns a {@link io.github.mahfaas.fraudshield.engine.RuleResult}. To avoid breaking
 * this contract (and adding a coupling from every rule to a shared context object),
 * we use a thread-local variable that is set by the engine immediately before invoking
 * the terminal rule and cleared in a {@code finally} block.
 * </p>
 *
 * <h3>Safety</h3>
 * <p>
 * The engine calls {@link #clear()} in a {@code finally} block to prevent context
 * leakage between Kafka consumer thread invocations.
 * </p>
 */
public final class RuleContext {

    private static final ThreadLocal<RuleContext> THREAD_LOCAL = new ThreadLocal<>();

    /** Total risk score accumulated so far by all preceding rules. */
    @Getter
    private final int accumulatedScore;

    /** The best (highest-severity) verdict produced by preceding rules. */
    @Getter
    private final Verdict priorVerdict;

    private RuleContext(int accumulatedScore, Verdict priorVerdict) {
        this.accumulatedScore = accumulatedScore;
        this.priorVerdict = priorVerdict;
    }

    /**
     * Sets the context for the current thread. Called by the rule engine
     * just before invoking {@link RiskScoreThresholdRule}.
     *
     * @param accumulatedScore total risk score from all preceding rules
     * @param priorVerdict     current best verdict before the final rule
     */
    public static void set(int accumulatedScore, Verdict priorVerdict) {
        THREAD_LOCAL.set(new RuleContext(accumulatedScore, priorVerdict));
    }

    /**
     * Returns the context for the current thread, or {@code null} if none was set.
     */
    public static RuleContext current() {
        return THREAD_LOCAL.get();
    }

    /**
     * Removes the context from the current thread.
     * Must be called in a {@code finally} block after rule evaluation is complete.
     */
    public static void clear() {
        THREAD_LOCAL.remove();
    }
}
