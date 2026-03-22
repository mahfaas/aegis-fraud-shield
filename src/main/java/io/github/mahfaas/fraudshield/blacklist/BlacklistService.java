package io.github.mahfaas.fraudshield.blacklist;

import io.github.mahfaas.fraudshield.engine.rules.BlacklistRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for managing blacklist entries.
 * <p>
 * Every mutation is persisted to PostgreSQL and immediately reflected
 * in the in-memory {@link BlacklistRule} for zero-downtime updates.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlacklistService {

    private final BlacklistRepository repository;
    private final BlacklistRule blacklistRule;

    @Transactional(readOnly = true)
    public List<BlacklistEntity> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<BlacklistEntity> findByType(BlacklistType type) {
        return repository.findByType(type);
    }

    @Transactional
    public BlacklistEntity addEntry(BlacklistType type, String value, String reason) {
        if (repository.existsByTypeAndValue(type, value)) {
            throw new IllegalArgumentException(
                    "Entry already exists: type=" + type + ", value=" + value);
        }

        BlacklistEntity entity = BlacklistEntity.builder()
                .type(type)
                .value(value)
                .reason(reason)
                .build();

        BlacklistEntity saved = repository.save(entity);
        syncToRule(type, value, true);

        log.info("Added blacklist entry: type={}, value={}", type, value);
        return saved;
    }

    @Transactional
    public void removeEntry(BlacklistType type, String value) {
        repository.deleteByTypeAndValue(type, value);
        syncToRule(type, value, false);

        log.info("Removed blacklist entry: type={}, value={}", type, value);
    }

    /**
     * Sync a single entry to/from the in-memory BlacklistRule.
     */
    private void syncToRule(BlacklistType type, String value, boolean add) {
        switch (type) {
            case IP -> {
                if (add) blacklistRule.addIp(value);
                else blacklistRule.removeIp(value);
            }
            case BIN -> {
                if (add) blacklistRule.addBin(value);
                else blacklistRule.removeBin(value);
            }
        }
    }
}
