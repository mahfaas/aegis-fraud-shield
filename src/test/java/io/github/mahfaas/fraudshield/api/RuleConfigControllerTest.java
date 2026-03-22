package io.github.mahfaas.fraudshield.api;

import io.github.mahfaas.fraudshield.engine.rules.AmountAnomalyRule;
import io.github.mahfaas.fraudshield.engine.rules.VelocityRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RuleConfigController.class)
class RuleConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AmountAnomalyRule amountAnomalyRule;

    @MockitoBean
    private VelocityRule velocityRule;

    @Test
    @DisplayName("GET /api/v1/rules/config — should return current config")
    void shouldGetConfig() throws Exception {
        when(amountAnomalyRule.getDeclineThreshold()).thenReturn(BigDecimal.valueOf(500000));
        when(amountAnomalyRule.getReviewThreshold()).thenReturn(BigDecimal.valueOf(100000));
        when(velocityRule.getMaxTransactions()).thenReturn(5);
        when(velocityRule.getWindowSeconds()).thenReturn(60L);

        mockMvc.perform(get("/api/v1/rules/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.AMOUNT_ANOMALY.declineThreshold").value(500000))
                .andExpect(jsonPath("$.VELOCITY.maxTransactions").value(5));
    }

    @Test
    @DisplayName("PUT /api/v1/rules/config/amount-anomaly — should update thresholds")
    void shouldUpdateAmountConfig() throws Exception {
        when(amountAnomalyRule.getDeclineThreshold()).thenReturn(BigDecimal.valueOf(300000));
        when(amountAnomalyRule.getReviewThreshold()).thenReturn(BigDecimal.valueOf(50000));

        String json = """
                {"declineThreshold": 300000, "reviewThreshold": 50000}
                """;

        mockMvc.perform(put("/api/v1/rules/config/amount-anomaly")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declineThreshold").value(300000));

        verify(amountAnomalyRule).setDeclineThreshold(BigDecimal.valueOf(300000));
        verify(amountAnomalyRule).setReviewThreshold(BigDecimal.valueOf(50000));
    }

    @Test
    @DisplayName("PUT /api/v1/rules/config/velocity — should update velocity config")
    void shouldUpdateVelocityConfig() throws Exception {
        when(velocityRule.getMaxTransactions()).thenReturn(10);
        when(velocityRule.getWindowSeconds()).thenReturn(120L);

        String json = """
                {"maxTransactions": 10, "windowSeconds": 120}
                """;

        mockMvc.perform(put("/api/v1/rules/config/velocity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxTransactions").value(10));

        verify(velocityRule).setMaxTransactions(10);
        verify(velocityRule).setWindowSeconds(120L);
    }
}
