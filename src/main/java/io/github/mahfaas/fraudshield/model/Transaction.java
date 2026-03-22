package io.github.mahfaas.fraudshield.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents an incoming financial transaction for fraud analysis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @NotBlank
    private String transactionId;

    @NotBlank
    private String accountId;

    @NotBlank
    private String cardBin;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    private String currency;

    private String merchantId;

    private String merchantCategory;

    private String sourceIp;

    @NotBlank
    private String country;

    @NotNull
    private Instant timestamp;
}
