package io.github.mahfaas.fraudshield.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fraud.api.rate-limit")
public class RateLimitProperties {
    
    /** Whether API rate limiting is enabled. */
    private boolean enabled = true;
    
    /** Maximum number of requests allowed within the window. */
    private int maxRequests = 100;
    
    /** The sliding window size in seconds. */
    private int windowSeconds = 60;
}
