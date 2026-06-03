package io.github.mahfaas.fraudshield.config;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JPA entity that maps the {@code rule_config} table created by Flyway migration V1.
 *
 * <h3>Hibernate ORM showcase</h3>
 * <ul>
 *   <li>{@code @OneToMany(cascade = ALL, orphanRemoval = true)} — full lifecycle
 *       management: adding/removing tags via the parent collection is automatically
 *       reflected in the database.</li>
 *   <li>{@code mappedBy = "ruleConfig"} — the FK lives in {@link RuleConfigTagEntity},
 *       making that the owning side; this entity is the inverse side.</li>
 *   <li>{@code @JdbcTypeCode(SqlTypes.JSON)} — Hibernate 6 native JSONB mapping for
 *       the {@code config_json} column (no manual serialisation needed).</li>
 * </ul>
 */
@Entity
@Table(name = "rule_config")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique name that identifies the rule (matches {@link io.github.mahfaas.fraudshield.engine.Rule#getName()}). */
    @Column(name = "rule_name", nullable = false, unique = true, length = 50)
    private String ruleName;

    /** Master switch — set to {@code false} to disable a rule without deleting its config. */
    @Column(nullable = false)
    private boolean enabled;

    /**
     * Rule-specific parameters stored as a JSONB blob.
     *
     * <p>Mapped as a {@code Map<String, Object>} so callers get a typed structure
     * without writing a custom deserialiser.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> configJson = Map.of();

    /** Last time the config was modified. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Metadata tags attached to this rule config.
     *
     * <p>{@code cascade = ALL} means persisting, merging, or deleting this entity
     * automatically propagates to all child tags.
     * {@code orphanRemoval = true} ensures that removing a tag from this list
     * deletes the corresponding row — no manual {@code DELETE} needed.
     */
    @OneToMany(
        mappedBy     = "ruleConfig",
        cascade      = CascadeType.ALL,
        orphanRemoval = true,
        fetch        = FetchType.LAZY
    )
    @Builder.Default
    private List<RuleConfigTagEntity> tags = new ArrayList<>();

    // ── Helper methods ───────────────────────────────────────────────────────

    /**
     * Adds a tag and sets the back-reference so Hibernate knows the owning side.
     *
     * @param key   tag key
     * @param value tag value
     */
    public void addTag(String key, String value) {
        RuleConfigTagEntity tag = RuleConfigTagEntity.of(key, value);
        tag.setRuleConfig(this);
        this.tags.add(tag);
    }

    /**
     * Removes all tags with the given key.
     *
     * @param key tag key to remove
     */
    public void removeTagsByKey(String key) {
        tags.removeIf(t -> t.getTagKey().equals(key));
    }

    /**
     * Returns a flat {@code key → value} map built from the tags collection via stream.
     *
     * <p>Demonstrates a stream pipeline over a JPA collection.
     *
     * @return immutable view of all tags as a map
     */
    public Map<String, String> tagsAsMap() {
        return tags.stream()
                .collect(Collectors.toUnmodifiableMap(
                        RuleConfigTagEntity::getTagKey,
                        RuleConfigTagEntity::getTagValue,
                        (a, b) -> a   // keep first on key collision
                ));
    }

    /** Sets the {@link #updatedAt} timestamp before every insert and update. */
    @PrePersist
    @PreUpdate
    private void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }
}
