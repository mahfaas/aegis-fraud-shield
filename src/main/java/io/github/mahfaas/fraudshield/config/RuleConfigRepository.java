package io.github.mahfaas.fraudshield.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link RuleConfigEntity}.
 *
 * <h3>Why JOIN FETCH?</h3>
 * <p>Without it, accessing {@code rc.getTags()} after loading a list of
 * {@code RuleConfigEntity} rows would fire one extra SELECT per entity (the
 * classic N+1 problem). {@code JOIN FETCH} collapses the parent + children
 * into a single SQL join, loading everything in one round-trip.
 */
public interface RuleConfigRepository extends JpaRepository<RuleConfigEntity, Long> {

    /**
     * Loads all rule configs together with their tags in a single SQL JOIN.
     *
     * <p>JPQL {@code JOIN FETCH} is the idiomatic Hibernate solution for eagerly
     * initialising a {@code LAZY} collection when you know you'll need it —
     * without switching the mapping to {@code EAGER} globally.
     *
     * @return all rule configs with tags initialised
     */
    @Query("SELECT rc FROM RuleConfigEntity rc LEFT JOIN FETCH rc.tags ORDER BY rc.ruleName")
    List<RuleConfigEntity> findAllWithTags();

    /**
     * Loads a single rule config together with its tags.
     *
     * @param ruleName the unique rule name
     * @return an {@link Optional} containing the entity with tags, or empty if not found
     */
    @Query("SELECT rc FROM RuleConfigEntity rc LEFT JOIN FETCH rc.tags WHERE rc.ruleName = :ruleName")
    Optional<RuleConfigEntity> findByRuleNameWithTags(@Param("ruleName") String ruleName);

    /**
     * Loads all <em>enabled</em> rule configs together with their tags.
     *
     * <p>Demonstrates combining a WHERE predicate with JOIN FETCH in one JPQL statement.
     *
     * @return enabled configs with tags initialised
     */
    @Query("SELECT rc FROM RuleConfigEntity rc LEFT JOIN FETCH rc.tags " +
           "WHERE rc.enabled = true ORDER BY rc.ruleName")
    List<RuleConfigEntity> findEnabledWithTags();
}
