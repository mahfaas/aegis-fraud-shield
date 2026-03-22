package io.github.mahfaas.fraudshield.model;

/**
 * The final decision on a transaction.
 */
public enum Verdict {
    /** Transaction is clean — proceed with payment. */
    APPROVED,
    /** Transaction is fraudulent — block immediately. */
    DECLINED,
    /** Transaction is suspicious — requires human review. */
    MANUAL_REVIEW
}
