package io.github.mahfaas.fraudshield.cases;

/**
 * Lifecycle states for a {@link FraudCase}.
 *
 * <pre>
 *   OPEN ──► INVESTIGATING ──► CLOSED_FRAUD
 *              ▲              └──► CLOSED_LEGITIMATE
 *              └───────────────────────┘
 *                   (explicit reopen)
 * </pre>
 *
 * <p>State transitions are validated in {@link FraudCaseService#updateStatus} —
 * only forward transitions are permitted to prevent accidental re-opening of
 * closed cases. The only way back from a closed state is the explicit,
 * reason-required {@link FraudCaseService#reopen} escape hatch, which is a
 * separate operation from the normal lifecycle advance.
 */
public enum FraudCaseStatus {

    /** Case created automatically from a MANUAL_REVIEW verdict. Awaiting assignment. */
    OPEN,

    /** An analyst has picked up the case and is actively reviewing evidence. */
    INVESTIGATING,

    /** Investigation complete — the transaction was confirmed as fraudulent. */
    CLOSED_FRAUD,

    /** Investigation complete — the transaction was found to be legitimate. */
    CLOSED_LEGITIMATE;

    /**
     * Returns {@code true} if this status represents a terminal (closed) state.
     * Closed cases cannot be transitioned to any other status.
     */
    public boolean isClosed() {
        return this == CLOSED_FRAUD || this == CLOSED_LEGITIMATE;
    }
}
