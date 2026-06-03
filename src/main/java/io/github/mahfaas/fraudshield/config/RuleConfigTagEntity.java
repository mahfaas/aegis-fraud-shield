package io.github.mahfaas.fraudshield.config;

import jakarta.persistence.*;
import lombok.*;

/**
 * JPA entity representing a single metadata tag attached to a {@link RuleConfigEntity}.
 *
 * <p>Models the <em>many</em> side of the {@code @OneToMany} / {@code @ManyToOne}
 * bidirectional relationship:
 * <ul>
 *   <li>Many tags → one rule config (foreign key {@code rule_config_id})</li>
 *   <li>{@link #ruleConfig} is the owning side — it holds the FK column</li>
 * </ul>
 *
 * <p>Tags are key-value pairs (e.g. {@code severity=HIGH}, {@code category=AMOUNT})
 * that let operators annotate rules without touching the JSONB config blob.
 */
@Entity
@Table(
    name = "rule_config_tag",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_rule_config_tag_key",
        columnNames = {"rule_config_id", "tag_key"}
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleConfigTagEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owning side of the bidirectional relationship.
     *
     * <p>{@code @ManyToOne(fetch = LAZY)} avoids an N+1 query when loading a list
     * of tags without needing the parent — the parent is loaded only when accessed.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_config_id", nullable = false)
    private RuleConfigEntity ruleConfig;

    /** Tag key — e.g. {@code "severity"}, {@code "category"}, {@code "owner"}. */
    @Column(name = "tag_key", nullable = false, length = 50)
    private String tagKey;

    /** Tag value — e.g. {@code "HIGH"}, {@code "AMOUNT"}, {@code "risk-team"}. */
    @Column(name = "tag_value", nullable = false, length = 255)
    private String tagValue;

    /**
     * Convenience factory for creating a detached tag (parent set separately).
     *
     * @param key   tag key
     * @param value tag value
     * @return a new tag without a parent assigned
     */
    public static RuleConfigTagEntity of(String key, String value) {
        return RuleConfigTagEntity.builder()
                .tagKey(key)
                .tagValue(value)
                .build();
    }
}
