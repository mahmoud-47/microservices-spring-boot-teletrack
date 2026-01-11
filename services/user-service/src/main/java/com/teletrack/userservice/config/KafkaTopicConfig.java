package com.teletrack.userservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic userRegisteredTopic() {
        return TopicBuilder.name("user.registered")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userApprovedTopic() {
        return TopicBuilder.name("user.approved")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userDeactivatedTopic() {
        return TopicBuilder.name("user.deactivated")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailVerificationTopic() {
        return TopicBuilder.name("email.verification")
                .partitions(3)
                .replicas(1)
                .build();
    }
}