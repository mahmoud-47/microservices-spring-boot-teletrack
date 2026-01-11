package com.teletrack.incidentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishEvent(String topic, Object event) {
        try {
            kafkaTemplate.send(topic, event);
            log.info("Published event to topic {}: {}", topic, event.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("Failed to publish event to topic {}: {}", topic, e.getMessage(), e);
        }
    }
}