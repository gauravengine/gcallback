package org.gaurav.gcallback.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    @Value("${app.kafka.topics.job-dispatch}")
    private String jobDispatchTopic;

    @Bean
    public NewTopic jobDispatchTopic() {
        return TopicBuilder.name(jobDispatchTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

}
