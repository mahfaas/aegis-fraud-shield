package io.github.mahfaas.fraudshield.api;

import io.github.mahfaas.fraudshield.config.RuleConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for querying and managing rule configurations stored in PostgreSQL.
 *
 * <p>Complements {@link RuleConfigController} (which operates on in-memory rule state)
 * with a DB-backed view that includes the {@code @OneToMany} tag relationship.
 *
 * <p>All read endpoints use a single JPQL {@code JOIN FETCH} under the hood —
 * no N+1 queries regardless of how many rules or tags exist.
 */
@RestController
@RequestMapping("/api/v1/rules/db-config")
@RequiredArgsConstructor
@Tag(name = "Rule DB Config", description = "DB-backed rule configurations with tag management (demonstrates @OneToMany JPA relationship)")
public class RuleConfigDbController {

    private final RuleConfigService ruleConfigService;

    /**
     * Returns all rule configs with their metadata tags.
     *
     * <p>Backed by JPQL {@code LEFT JOIN FETCH rc.tags} — all data is loaded
     * in a single SQL query.
     */
    @GetMapping
    @Operation(
            summary = "Get all rule configs with tags",
            description = "Returns all rule configurations from PostgreSQL, each with their @OneToMany metadata tags. " +
                          "Uses a JPQL JOIN FETCH to avoid N+1 queries."
    )
    public List<RuleConfigService.RuleConfigView> getAll() {
        return ruleConfigService.getAllWithTags();
    }

    /**
     * Returns only enabled rule configs with their tags.
     */
    @GetMapping("/enabled")
    @Operation(
            summary = "Get enabled rule configs with tags",
            description = "Returns only enabled rule configurations. Uses a JPQL WHERE + JOIN FETCH in one query."
    )
    public List<RuleConfigService.RuleConfigView> getEnabled() {
        return ruleConfigService.getEnabledWithTags();
    }

    /**
     * Returns a single rule config by name, with its tags.
     *
     * @param ruleName rule identifier (e.g. {@code BLACKLIST}, {@code VELOCITY})
     */
    @GetMapping("/{ruleName}")
    @Operation(
            summary = "Get a specific rule config with tags",
            description = "Returns a single rule configuration by name, including its tags. Throws 404 if not found."
    )
    public RuleConfigService.RuleConfigView getOne(
            @Parameter(description = "Rule name, e.g. BLACKLIST, VELOCITY, AMOUNT_ANOMALY")
            @PathVariable String ruleName) {
        return ruleConfigService.getByRuleName(ruleName);
    }

    /**
     * Adds or updates a metadata tag on a rule config.
     *
     * <p>Demonstrates the {@code @OneToMany} cascade: the tag is added by mutating
     * the parent collection — Hibernate handles the {@code INSERT} on flush.
     *
     * @param ruleName the rule to tag
     * @param request  the tag key and value
     */
    @PutMapping("/{ruleName}/tags")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Add or update a tag on a rule config",
            description = "Adds (or overwrites) a key-value tag on the rule config. " +
                          "Uses @OneToMany cascade — no explicit tag save needed."
    )
    public RuleConfigService.RuleConfigView upsertTag(
            @PathVariable String ruleName,
            @RequestBody TagRequest request) {
        return ruleConfigService.upsertTag(ruleName, request.key(), request.value());
    }

    /**
     * Removes a metadata tag from a rule config.
     *
     * <p>Demonstrates {@code orphanRemoval = true}: removing the tag from the
     * parent's collection triggers an automatic {@code DELETE}.
     *
     * @param ruleName the rule to modify
     * @param key      the tag key to remove
     */
    @DeleteMapping("/{ruleName}/tags/{key}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Remove a tag from a rule config",
            description = "Removes the tag with the given key. " +
                          "Uses orphanRemoval=true — Hibernate deletes the row automatically."
    )
    public RuleConfigService.RuleConfigView removeTag(
            @PathVariable String ruleName,
            @PathVariable String key) {
        return ruleConfigService.removeTag(ruleName, key);
    }

    /**
     * Request body for tag upsert operations.
     */
    public record TagRequest(String key, String value) {}
}
