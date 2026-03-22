package io.github.mahfaas.fraudshield.blacklist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for blacklist entries.
 */
@Repository
public interface BlacklistRepository extends JpaRepository<BlacklistEntity, Long> {

    List<BlacklistEntity> findByType(BlacklistType type);

    Optional<BlacklistEntity> findByTypeAndValue(BlacklistType type, String value);

    boolean existsByTypeAndValue(BlacklistType type, String value);

    void deleteByTypeAndValue(BlacklistType type, String value);
}
