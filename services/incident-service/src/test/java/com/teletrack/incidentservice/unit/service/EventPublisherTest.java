package com.teletrack.incidentservice.unit.service;

import com.teletrack.incidentservice.service.EventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("EventPublisher Unit Tests")
class EventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private EventPublisher eventPublisher;

    @Test
    @DisplayName("publishEvent — sends to correct topic")
    void publishEvent_SendsToTopic() {
        eventPublisher.publishEvent("incident.created", "payload");
        verify(kafkaTemplate).send("incident.created", "payload");
    }

    @Test
    @DisplayName("publishEvent — exception does not propagate")
    void publishEvent_ExceptionDoesNotPropagate() {
        when(kafkaTemplate.send(any(String.class), any()))
                .thenThrow(new RuntimeException("Kafka down"));
        assertDoesNotThrow(() -> eventPublisher.publishEvent("incident.created", "payload"));
    }
}
