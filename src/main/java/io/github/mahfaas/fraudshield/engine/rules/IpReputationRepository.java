package io.github.mahfaas.fraudshield.engine.rules;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link IpReputationEntity}.
 *
 * <p>Inherits standard CRUD from {@link JpaRepository}. {@link IpReputationRule}
 * loads the full table into memory on startup via {@code findAll()} — the table
 * is expected to stay small (curated IP ranges), so no pagination is needed.
 */
public interface IpReputationRepository extends JpaRepository<IpReputationEntity, Long> {
}
