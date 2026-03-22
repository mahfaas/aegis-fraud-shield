package io.github.mahfaas.fraudshield.api;

import io.github.mahfaas.fraudshield.producer.TransactionProducer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoint to trigger synthetic load generation.
 */
@RestController
@RequestMapping("/api/v1/producer")
@RequiredArgsConstructor
@Tag(name = "Load Generator", description = "Generate synthetic transactions for testing")
public class ProducerController {

    private final TransactionProducer transactionProducer;

    @PostMapping("/generate")
    @Operation(summary = "Generate a batch of synthetic transactions and send to Kafka")
    public Map<String, Object> generate(@RequestParam(defaultValue = "100") int count) {
        int sent = transactionProducer.generateBatch(count);
        return Map.of(
                "generated", sent,
                "message", "Sent " + sent + " transactions to Kafka"
        );
    }
}
