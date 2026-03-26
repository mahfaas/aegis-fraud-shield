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

    private Transaction transaction;
    private Verdict verdict;
    private List<String> reasons;
    private int totalRiskScore;
    private Instant processedAt;
}
