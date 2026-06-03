package io.github.mahfaas.fraudshield.model;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Represents an incoming financial transaction for fraud analysis")
public class Transaction {

    @NotBlank
    @Schema(description = "Unique identifier for the transaction", example = "tx-123456789")
    private String transactionId;

    @NotBlank
    @Schema(description = "Account identifier originating the transaction", example = "ACC-998877")
    private String accountId;

    @NotBlank
    @Schema(description = "First 6 digits of the credit card (Bank Identification Number)", example = "411111")
    private String cardBin;

    @NotNull
    @Positive
    @Schema(description = "Transaction amount in major currency units", example = "1500.00")
    private BigDecimal amount;

    @NotBlank
    @Schema(description = "ISO 4217 currency code", example = "USD")
    private String currency;

    @Schema(description = "Merchant identifier", example = "MCH-001")
    private String merchantId;

    @Schema(description = "Merchant category or industry type", example = "electronics")
    private String merchantCategory;

    @Schema(description = "IP address where the transaction originated", example = "192.168.1.100")
    private String sourceIp;

    @NotBlank
    @Schema(description = "Country code of origin", example = "US")
    private String country;

    @NotNull
    @Schema(description = "UTC timestamp of the transaction")
    private Instant timestamp;
}
