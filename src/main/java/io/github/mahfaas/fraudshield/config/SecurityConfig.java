package io.github.mahfaas.fraudshield.config;

import io.github.mahfaas.fraudshield.security.RateLimitFilter;
import io.github.mahfaas.fraudshield.security.RateLimitProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Global security and filter configuration.
 *
 * <p>Registers the {@link RateLimitFilter} strictly for API paths.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public RateLimitFilter rateLimitFilter(RateLimitProperties properties, StringRedisTemplate redisTemplate) {
        return new RateLimitFilter(properties, redisTemplate);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter rateLimitFilter) {
        FilterRegistrationBean<RateLimitFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(rateLimitFilter);
        registrationBean.addUrlPatterns("/api/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registrationBean;
    }
}
