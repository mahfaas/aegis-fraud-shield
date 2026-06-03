package io.github.mahfaas.fraudshield.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global OpenAPI / Swagger configuration.
 *
 * <p>Configures the main Swagger UI page metadata (title, description, version).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Aegis Fraud-Shield API")
                        .version("1.0.0")
                        .description("REST API for the Aegis Enterprise Fraud Detection Engine. " +
                                     "Provides endpoints for querying audit logs, managing rule configurations, " +
                                     "and generating synthetic transactions for load testing.")
                        .contact(new Contact()
                                .name("Aegis Risk Team")
                                .url("https://github.com/mahfaas/aegis-fraud-shield"))
                        .license(new License().name("Apache 2.0").url("https://springdoc.org")));
    }
}
