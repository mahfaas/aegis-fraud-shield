package io.github.mahfaas.fraudshield.engine.rules;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity mapped to the {@code ip_reputation} table — backs {@link IpReputationRule}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ip_reputation")
public class IpReputationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Source-IP prefix to match against, e.g. {@code "185.220."}. */
    @Column(name = "ip_prefix", nullable = false, unique = true, length = 45)
    private String ipPrefix;

    /** ISO 3166-1 alpha-2 country this prefix is known to be located in. */
    @Column(nullable = false, length = 2)
    private String country;

    /** Risk category, e.g. {@code TOR_EXIT}, {@code DATACENTER}, {@code VPN}. */
    @Column(nullable = false, length = 20)
    private String category;

    /** Base risk contribution for this category: 0, 50, or 100 (same convention as {@code RuleResult}). */
    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
