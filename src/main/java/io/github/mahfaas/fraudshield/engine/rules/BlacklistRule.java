package io.github.mahfaas.fraudshield.engine.rules;

import io.github.mahfaas.fraudshield.engine.Rule;
import io.github.mahfaas.fraudshield.engine.RuleResult;
import io.github.mahfaas.fraudshield.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocks transactions originating from blacklisted IP addresses or card BIN codes.
 * <p>
 * Uses {@link ConcurrentHashMap}-backed sets for O(1) lookups and thread safety.
 * In production, the blacklist is synced from PostgreSQL on startup and updated
 * via the REST API at runtime.
 * </p>
 */
@Slf4j
@Component
public class BlacklistRule implements Rule {

    private static final String RULE_NAME = "BLACKLIST";

    private final Set<String> blacklistedIps = ConcurrentHashMap.newKeySet();
    private final Set<String> blacklistedBins = ConcurrentHashMap.newKeySet();

    @Override
    public RuleResult evaluate(Transaction transaction) {
        if (transaction.getSourceIp() != null
                && blacklistedIps.contains(transaction.getSourceIp())) {
            return RuleResult.decline(RULE_NAME,
                    "IP address " + transaction.getSourceIp() + " is blacklisted");
        }

        if (transaction.getCardBin() != null
                && blacklistedBins.contains(transaction.getCardBin())) {
            return RuleResult.decline(RULE_NAME,
                    "Card BIN " + transaction.getCardBin() + " is blacklisted");
        }

        return RuleResult.approve(RULE_NAME);
    }

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public int getOrder() {
        return 10; // run first — cheapest check
    }

    public void addIp(String ip) {
        blacklistedIps.add(ip);
        log.info("Added IP to blacklist: {}", ip);
    }

    public void removeIp(String ip) {
        blacklistedIps.remove(ip);
        log.info("Removed IP from blacklist: {}", ip);
    }

    public void addBin(String bin) {
        blacklistedBins.add(bin);
        log.info("Added BIN to blacklist: {}", bin);
    }

    public void removeBin(String bin) {
        blacklistedBins.remove(bin);
        log.info("Removed BIN from blacklist: {}", bin);
    }

    public Set<String> getBlacklistedIps() {
        return Set.copyOf(blacklistedIps);
    }

    public Set<String> getBlacklistedBins() {
        return Set.copyOf(blacklistedBins);
    }

    /** Convenience method for testing and status checks. */
    public boolean isIpBlacklisted(String ip) {
        return blacklistedIps.contains(ip);
    }

    /** Convenience method for testing and status checks. */
    public boolean isBinBlacklisted(String bin) {
        return blacklistedBins.contains(bin);
    }

    /**
     * Clears all in-memory entries. Intended for test setup / full reloads.
     */
    public void clear() {
        blacklistedIps.clear();
        blacklistedBins.clear();
        log.info("Cleared all blacklist entries from memory");
    }

    /**
     * Bulk-load blacklist entries (called on startup from DB).
     * Replaces all existing in-memory IPs.
     */
    public void loadIps(Iterable<String> ips) {
        blacklistedIps.clear();
        ips.forEach(blacklistedIps::add);
        log.info("Loaded {} IPs into blacklist", blacklistedIps.size());
    }

    /**
     * Bulk-load blacklist entries (called on startup from DB).
     * Replaces all existing in-memory BINs.
     */
    public void loadBins(Iterable<String> bins) {
        blacklistedBins.clear();
        bins.forEach(blacklistedBins::add);
        log.info("Loaded {} BINs into blacklist", blacklistedBins.size());
    }
}
