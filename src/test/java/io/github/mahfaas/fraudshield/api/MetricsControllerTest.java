package io.github.mahfaas.fraudshield.api;

import io.github.mahfaas.fraudshield.engine.RuleEngine;
import io.github.mahfaas.fraudshield.metrics.FraudMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsController — Unit Tests")
class MetricsControllerTest {

    @Mock private FraudMetrics fraudMetrics;
    @Mock private RuleEngine ruleEngine;

    @InjectMocks
    private MetricsController controller;

    @Test
    @DisplayName("Should return all expected fields in summary")
    void shouldReturnAllFields() {
        when(fraudMetrics.getReceivedCount()).thenReturn(1000L);
        when(fraudMetrics.getApprovedCount()).thenReturn(850L);
        when(fraudMetrics.getDeclinedCount()).thenReturn(100L);
        when(fraudMetrics.getManualReviewCount()).thenReturn(50L);
        when(fraudMetrics.getDlqCount()).thenReturn(0L);
        when(ruleEngine.getRuleCount()).thenReturn(4);

        Map<String, Object> summary = controller.getSummary();

        assertAll(
                () -> assertEquals(1000L, summary.get("received")),
                () -> assertEquals(850L,  summary.get("approved")),
                () -> assertEquals(100L,  summary.get("declined")),
                () -> assertEquals(50L,   summary.get("manualReview")),
                () -> assertEquals(0L,    summary.get("dlq")),
                () -> assertEquals(4,     summary.get("activeRules"))
        );
    }

    @Test
    @DisplayName("Should return zero counts when no transactions processed")
    void shouldReturnZeroCountsInitially() {
        when(fraudMetrics.getReceivedCount()).thenReturn(0L);
        when(fraudMetrics.getApprovedCount()).thenReturn(0L);
        when(fraudMetrics.getDeclinedCount()).thenReturn(0L);
        when(fraudMetrics.getManualReviewCount()).thenReturn(0L);
        when(fraudMetrics.getDlqCount()).thenReturn(0L);
        when(ruleEngine.getRuleCount()).thenReturn(4);

        Map<String, Object> summary = controller.getSummary();

        assertEquals(0L, summary.get("received"));
        assertEquals(4,  summary.get("activeRules"));
    }

    @Test
    @DisplayName("Should reflect updated rule count from RuleEngine")
    void shouldReflectRuleCount() {
        when(ruleEngine.getRuleCount()).thenReturn(5);
        when(fraudMetrics.getReceivedCount()).thenReturn(0L);
        when(fraudMetrics.getApprovedCount()).thenReturn(0L);
        when(fraudMetrics.getDeclinedCount()).thenReturn(0L);
        when(fraudMetrics.getManualReviewCount()).thenReturn(0L);
        when(fraudMetrics.getDlqCount()).thenReturn(0L);

        Map<String, Object> summary = controller.getSummary();

        assertEquals(5, summary.get("activeRules"),
                "activeRules must reflect the current number of rules from RuleEngine");
    }

    @Test
    @DisplayName("Summary should contain exactly 6 keys")
    void shouldContainExactly6Keys() {
        when(fraudMetrics.getReceivedCount()).thenReturn(0L);
        when(fraudMetrics.getApprovedCount()).thenReturn(0L);
        when(fraudMetrics.getDeclinedCount()).thenReturn(0L);
        when(fraudMetrics.getManualReviewCount()).thenReturn(0L);
        when(fraudMetrics.getDlqCount()).thenReturn(0L);
        when(ruleEngine.getRuleCount()).thenReturn(4);

        Map<String, Object> summary = controller.getSummary();

        assertEquals(6, summary.size(),
                "Summary must have exactly 6 fields: received, approved, declined, manualReview, dlq, activeRules");
    }
}
