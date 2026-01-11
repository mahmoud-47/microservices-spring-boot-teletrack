package com.teletrack.userservice.service;

import com.teletrack.commonutils.event.UserEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishEvent(String topic, UserEvent event) {
        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, event.getUserId(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Event published successfully to topic {}: eventId={}, userId={}",
                            topic, event.getEventId(), event.getUserId());
                } else {
                    log.error("Failed to publish event to topic {}: eventId={}, error={}",
                            topic, event.getEventId(), ex.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Error publishing event to topic {}: {}", topic, e.getMessage(), e);
        }
    }
}