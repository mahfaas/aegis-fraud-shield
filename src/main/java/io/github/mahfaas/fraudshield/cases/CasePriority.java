package io.github.mahfaas.fraudshield.cases;

/**
 * Triage priority for a {@link FraudCase}, derived from the composite risk
 * score at case-creation time (see {@link FraudCaseService#computePriority}).
 *
 * <p>Stored on the entity rather than computed on read so it stays stable
 * for a given case and can be filtered/paginated efficiently in SQL.
 */
public enum CasePriority {

    /** riskScore below 50 — a soft signal escalated to review, low urgency. */
    LOW,

    /** riskScore 50–74 — worth investigating soon. */
    MEDIUM,

    /** riskScore 75 and above — closest to the auto-decline threshold, review first. */
    HIGH
}
