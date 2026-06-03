package io.github.mahfaas.fraudshield.model;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "A transaction enriched with the fraud-check verdict and reasons")
public class VerdictedTransaction {

    @Schema(description = "Original transaction data that was evaluated")
    private Transaction transaction;

    @Schema(description = "Final verdict from the Rule Engine")
    private Verdict verdict;

    @Schema(description = "Human-readable reasons collected from each rule that triggered")
    private List<String> reasons;

    @Schema(description = "Aggregated risk score from all evaluated rules", example = "100")
    private int totalRiskScore;

    @Schema(description = "Timestamp when the verdict was produced")
    private Instant processedAt;
}
