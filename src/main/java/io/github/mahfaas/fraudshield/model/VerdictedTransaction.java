package io.github.mahfaas.fraudshield.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * A transaction enriched with the fraud-check verdict and reasons.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerdictedTransaction {

    /** Original transaction data. */
    private Transaction transaction;

    /** Final verdict. */
    private Verdict verdict;

    /** Human-readable reasons collected from each rule that triggered. */
    private List<String> reasons;

    /** Timestamp when the verdict was produced. */
    private Instant processedAt;
}
