package io.github.mahfaas.fraudshield.alert;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fraud.alerts")
public class AlertProperties {

    /** Whether webhook alerting is enabled. Disabled by default — no webhook URL is configured out of the box. */
    private boolean enabled = false;

    /** Destination URL that DECLINED / MANUAL_REVIEW verdicts are POSTed to. */
    private String webhookUrl;

    /** Connect and read timeout for the webhook HTTP call, in milliseconds. */
    private int timeoutMs = 3000;
}
