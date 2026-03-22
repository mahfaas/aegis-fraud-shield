package io.github.mahfaas.fraudshield.blacklist;

import io.github.mahfaas.fraudshield.engine.rules.BlacklistRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Loads blacklist entries from PostgreSQL into the in-memory {@link BlacklistRule}
 * on application startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlacklistLoader implements ApplicationRunner {

    private final BlacklistRepository repository;
    private final BlacklistRule blacklistRule;

    @Override
    public void run(ApplicationArguments args) {
        List<BlacklistEntity> ips = repository.findByType(BlacklistType.IP);
        List<BlacklistEntity> bins = repository.findByType(BlacklistType.BIN);

        blacklistRule.loadIps(ips.stream().map(BlacklistEntity::getValue).toList());
        blacklistRule.loadBins(bins.stream().map(BlacklistEntity::getValue).toList());

        log.info("BlacklistLoader: loaded {} IPs and {} BINs from database", ips.size(), bins.size());
    }
}
