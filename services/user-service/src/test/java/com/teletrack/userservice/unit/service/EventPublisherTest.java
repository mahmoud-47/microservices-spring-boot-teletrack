package com.teletrack.userservice.unit.service;

import com.teletrack.commonutils.event.UserRegisteredEvent;
import com.teletrack.userservice.service.EventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
@DisplayName("EventPublisher Unit Tests")
class EventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private EventPublisher eventPublisher;

    private UserRegisteredEvent buildEvent() {
        return UserRegisteredEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("USER_REGISTERED")
                .timestamp(LocalDateTime.now())
                .correlationId(UUID.randomUUID().toString())
                .userId(UUID.randomUUID().toString())
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .role("OPERATOR")
                .build();
    }

    @Test
    @DisplayName("Should publish event successfully without throwing")
    @SuppressWarnings("unchecked")
    void publishEvent_Success_NoExceptionThrown() {
        UserRegisteredEvent event = buildEvent();
        SendResult<String, Object> mockResult = mock(SendResult.class);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

        assertThatCode(() -> eventPublisher.publishEvent("user.registered", event))
                .doesNotThrowAnyException();

        verify(kafkaTemplate).send("user.registered", event.getUserId(), event);
    }

    @Test
    @DisplayName("Should not propagate exception when Kafka send future fails")
    void publishEvent_KafkaFutureFailure_DoesNotPropagateException() {
        UserRegisteredEvent event = buildEvent();
        CompletableFuture<SendResult<String, Object>> failedFuture =
                CompletableFuture.failedFuture(new RuntimeException("Kafka broker unavailable"));

        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture);

        assertThatCode(() -> eventPublisher.publishEvent("user.registered", event))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should not propagate exception when KafkaTemplate.send throws immediately")
    void publishEvent_KafkaSendThrowsImmediately_CaughtByCatch() {
        UserRegisteredEvent event = buildEvent();
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka connection refused"));

        assertThatCode(() -> eventPublisher.publishEvent("user.registered", event))
                .doesNotThrowAnyException();
    }
}
