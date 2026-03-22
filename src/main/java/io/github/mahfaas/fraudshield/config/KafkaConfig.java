package io.github.mahfaas.fraudshield.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Creates Kafka topics used by the fraud-detection pipeline.
 */
@Configuration
public class KafkaConfig {

    @Value("${fraud.kafka.topic-in}")
    private String topicIn;

    @Value("${fraud.kafka.topic-out}")
    private String topicOut;

    @Value("${fraud.kafka.topic-dlq}")
    private String topicDlq;

    @Bean
    public NewTopic transactionsRawTopic() {
        return TopicBuilder.name(topicIn)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transactionsVerdictedTopic() {
        return TopicBuilder.name(topicOut)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transactionsDlqTopic() {
        return TopicBuilder.name(topicDlq)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
