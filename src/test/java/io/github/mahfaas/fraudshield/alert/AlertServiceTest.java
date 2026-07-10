package io.github.mahfaas.fraudshield.alert;

import io.github.mahfaas.fraudshield.metrics.FraudMetrics;
import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.Verdict;
import io.github.mahfaas.fraudshield.model.VerdictedTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link AlertService}.
 *
 * <h3>Testing techniques demonstrated</h3>
 * <ul>
 *   <li>{@link MockRestServiceServer} — verifies the real outbound HTTP call
 *       (method, URL, body-triggering dispatch) without hitting the network</li>
 *   <li>{@link Mock} on {@link FraudMetrics} — asserts alert success/failure counters</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private FraudMetrics metrics;

    private AlertService alertService;
    private MockRestServiceServer mockServer;
    private AlertProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AlertProperties();
        properties.setEnabled(true);
        properties.setWebhookUrl("http://alerts.example.test/webhook");

        alertService = new AlertService(properties, metrics, new RestTemplateBuilder());
        mockServer = MockRestServiceServer.bindTo(alertService.restTemplate).build();
    }

    private VerdictedTransaction buildVerdictedTx(Verdict verdict) {
        Transaction tx = Transaction.builder()
                .transactionId("tx-001")
                .accountId("ACC-001")
                .cardBin("411111")
                .amount(BigDecimal.valueOf(750_000))
                .currency("USD")
                .country("US")
                .sourceIp("1.2.3.4")
                .timestamp(Instant.now())
                .build();

        return VerdictedTransaction.builder()
                .transaction(tx)
                .verdict(verdict)
                .reasons(verdict == Verdict.APPROVED ? Collections.emptyList() : List.of("[RULE] triggered"))
                .totalRiskScore(verdict == Verdict.DECLINED ? 100 : 50)
                .processedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("notify() POSTs to the webhook URL for a DECLINED verdict")
    void notifyPostsForDeclined() {
        mockServer.expect(requestTo("http://alerts.example.test/webhook"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        alertService.notify(buildVerdictedTx(Verdict.DECLINED));

        mockServer.verify();
        verify(metrics).recordAlertSent();
    }

    @Test
    @DisplayName("notify() POSTs to the webhook URL for a MANUAL_REVIEW verdict")
    void notifyPostsForManualReview() {
        mockServer.expect(requestTo("http://alerts.example.test/webhook"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        alertService.notify(buildVerdictedTx(Verdict.MANUAL_REVIEW));

        mockServer.verify();
        verify(metrics).recordAlertSent();
    }

    @Test
    @DisplayName("notify() does nothing for an APPROVED verdict")
    void notifyNoOpForApproved() {
        alertService.notify(buildVerdictedTx(Verdict.APPROVED));

        mockServer.verify(); // no expectations set, so no request must have been made
        verifyNoInteractions(metrics);
    }

    @Test
    @DisplayName("notify() does nothing when alerting is disabled")
    void notifyNoOpWhenDisabled() {
        properties.setEnabled(false);

        alertService.notify(buildVerdictedTx(Verdict.DECLINED));

        mockServer.verify();
        verifyNoInteractions(metrics);
    }

    @Test
    @DisplayName("notify() swallows errors and records a failure metric instead of throwing")
    void notifySwallowsServerError() {
        mockServer.expect(requestTo("http://alerts.example.test/webhook"))
                .andRespond(withServerError());

        alertService.notify(buildVerdictedTx(Verdict.DECLINED));

        mockServer.verify();
        verify(metrics).recordAlertFailed();
    }
}
