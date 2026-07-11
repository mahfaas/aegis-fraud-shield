package io.github.mahfaas.fraudshield.engine.rules;

import io.github.mahfaas.fraudshield.engine.Rule;
import io.github.mahfaas.fraudshield.engine.RuleResult;
import io.github.mahfaas.fraudshield.model.Transaction;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flags transactions whose source IP matches a known-risky range and/or whose
 * IP-implied country doesn't match the transaction's declared country.
 *
 * <p>Backed by a small, curated table ({@code ip_reputation}) of IP prefixes mapped
 * to a country and risk category — loaded into memory on startup, the same pattern
 * as {@link io.github.mahfaas.fraudshield.blacklist.BlacklistRule}. Not a full
 * IP-geolocation service — matching is a simple longest-prefix lookup over a small,
 * curated dataset.
 *
 * <h3>How it works</h3>
 * <ul>
 *   <li>No matching prefix → APPROVED (no data, no penalty)</li>
 *   <li>Matching prefix with {@code riskScore == 100} → DECLINED</li>
 *   <li>Matching prefix with {@code riskScore >= 50}, or a country mismatch between the
 *       matched entry and the transaction's declared country → MANUAL_REVIEW</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fraud.rules.ip-reputation.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class IpReputationRule implements Rule {

    private static final String RULE_NAME = "IP_REPUTATION";

    private final IpReputationRepository repository;

    private final ConcurrentHashMap<String, IpReputationInfo> byPrefix = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        load(repository.findAll());
    }

    @Override
    public RuleResult evaluate(Transaction transaction) {
        String sourceIp = transaction.getSourceIp();
        if (sourceIp == null || sourceIp.isBlank()) {
            return RuleResult.approve(RULE_NAME);
        }

        IpReputationInfo match = findLongestMatch(sourceIp);
        if (match == null) {
            return RuleResult.approve(RULE_NAME);
        }

        boolean geoMismatch = !match.country().equalsIgnoreCase(transaction.getCountry());

        if (match.riskScore() >= 100) {
            return RuleResult.decline(RULE_NAME,
                    "IP " + sourceIp + " matches high-risk range (" + match.category() + ")");
        }

        if (match.riskScore() >= 50 || geoMismatch) {
            String reason = geoMismatch
                    ? "IP " + sourceIp + " geolocates to " + match.country() + " but transaction declares "
                            + transaction.getCountry() + " (" + match.category() + ")"
                    : "IP " + sourceIp + " matches risky range (" + match.category() + ")";
            return RuleResult.manualReview(RULE_NAME, reason);
        }

        return RuleResult.approve(RULE_NAME);
    }

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public int getOrder() {
        return 12; // after BlacklistRule(10), before MerchantCategoryRule(15)
    }

    /**
     * Returns the entry whose prefix is both a match and the longest (most specific) one,
     * so a more specific range takes precedence over a broader overlapping one.
     */
    private IpReputationInfo findLongestMatch(String sourceIp) {
        return byPrefix.entrySet().stream()
                .filter(e -> sourceIp.startsWith(e.getKey()))
                .max(Comparator.comparingInt(e -> e.getKey().length()))
                .map(java.util.Map.Entry::getValue)
                .orElse(null);
    }

    /**
     * Bulk-loads entries into memory, replacing all existing ones.
     * Called on startup ({@link #init()}) and directly by tests.
     */
    public void load(Collection<IpReputationEntity> entries) {
        byPrefix.clear();
        entries.forEach(e -> byPrefix.put(e.getIpPrefix(),
                new IpReputationInfo(e.getCountry(), e.getCategory(), e.getRiskScore())));
        log.info("IpReputationRule: loaded {} IP reputation entries", byPrefix.size());
    }

    private record IpReputationInfo(String country, String category, int riskScore) {}
}
