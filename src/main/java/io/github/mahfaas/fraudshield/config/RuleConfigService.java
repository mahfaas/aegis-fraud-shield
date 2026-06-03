package io.github.mahfaas.fraudshield.config;

import io.github.mahfaas.fraudshield.exception.RuleNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service layer for managing {@link RuleConfigEntity} and their {@link RuleConfigTagEntity} children.
 *
 * <p>Exposes tag read/write operations that exercise the {@code @OneToMany / @ManyToOne}
 * relationship through the JPA cascade mechanism — no manual INSERT/DELETE is written;
 * mutating the parent's {@code tags} collection is sufficient.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleConfigService {

    private final RuleConfigRepository repository;

    // ── Read ─────────────────────────────────────────────────────────────────

    /**
     * Returns all rule configs with their tags, loaded via a single JOIN FETCH query.
     *
     * <p>The result is projected to a list of {@link RuleConfigView} records using
     * a stream {@code map} pipeline, keeping entities out of the API layer.
     *
     * @return immutable list of config views
     */
    @Transactional(readOnly = true)
    public List<RuleConfigView> getAllWithTags() {
        return repository.findAllWithTags()
                .stream()
                .map(RuleConfigView::from)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns a single rule config by name, or throws {@link RuleNotFoundException}.
     *
     * @param ruleName the rule name to look up
     * @return config view with tags
     */
    @Transactional(readOnly = true)
    public RuleConfigView getByRuleName(String ruleName) {
        return repository.findByRuleNameWithTags(ruleName)
                .map(RuleConfigView::from)
                .orElseThrow(() -> new RuleNotFoundException(ruleName));
    }

    /**
     * Returns only enabled rule configs with their tags.
     *
     * @return list of enabled config views
     */
    @Transactional(readOnly = true)
    public List<RuleConfigView> getEnabledWithTags() {
        return repository.findEnabledWithTags()
                .stream()
                .map(RuleConfigView::from)
                .collect(Collectors.toUnmodifiableList());
    }

    // ── Tag management ───────────────────────────────────────────────────────

    /**
     * Adds or updates a tag on a rule config.
     *
     * <p>Demonstrates {@code @OneToMany} cascade in action:
     * <ol>
     *   <li>Load the parent entity (tags collection is JOIN FETCHed)</li>
     *   <li>Call {@link RuleConfigEntity#addTag(String, String)} to mutate the collection</li>
     *   <li>Spring's {@code @Transactional} flushes the change automatically on commit</li>
     * </ol>
     * No explicit {@code repository.save()} for the tag is needed — the parent cascade handles it.
     *
     * @param ruleName rule to update
     * @param key      tag key
     * @param value    tag value
     * @return updated config view
     */
    @Transactional
    public RuleConfigView upsertTag(String ruleName, String key, String value) {
        RuleConfigEntity entity = repository.findByRuleNameWithTags(ruleName)
                .orElseThrow(() -> new RuleNotFoundException(ruleName));

        // Remove old tag with the same key (orphanRemoval handles the DELETE)
        entity.removeTagsByKey(key);
        // Add the new tag (CascadeType.ALL handles the INSERT)
        entity.addTag(key, value);

        log.info("Upserted tag [{}={}] on rule '{}'", key, value, ruleName);
        return RuleConfigView.from(entity);
    }

    /**
     * Removes a tag from a rule config.
     *
     * <p>{@code orphanRemoval = true} on the parent's {@code @OneToMany} ensures
     * that removing the tag from the collection triggers a {@code DELETE} automatically.
     *
     * @param ruleName rule to update
     * @param key      tag key to remove
     * @return updated config view
     */
    @Transactional
    public RuleConfigView removeTag(String ruleName, String key) {
        RuleConfigEntity entity = repository.findByRuleNameWithTags(ruleName)
                .orElseThrow(() -> new RuleNotFoundException(ruleName));

        entity.removeTagsByKey(key);
        log.info("Removed tag [{}] from rule '{}'", key, ruleName);
        return RuleConfigView.from(entity);
    }

    // ── Projection record ────────────────────────────────────────────────────

    /**
     * Immutable view DTO projected from {@link RuleConfigEntity}.
     *
     * <p>Tags are flattened to a {@code Map<String, String>} via
     * {@link RuleConfigEntity#tagsAsMap()} — keeping entity internals
     * out of the API response.
     */
    public record RuleConfigView(
            Long id,
            String ruleName,
            boolean enabled,
            Map<String, Object> configJson,
            Map<String, String> tags
    ) {
        static RuleConfigView from(RuleConfigEntity e) {
            return new RuleConfigView(
                    e.getId(),
                    e.getRuleName(),
                    e.isEnabled(),
                    e.getConfigJson(),
                    e.tagsAsMap()
            );
        }
    }
}
