package com.teletrack.notificationservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.teletrack.commonutils.enums.IncidentPriority;
import com.teletrack.commonutils.event.IncidentCreatedEvent;
import com.teletrack.commonutils.event.UserRegisteredEvent;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "user.registered", "email.verification", "user.approved", "user.deactivated",
                "incident.created", "incident.assigned", "incident.status.changed",
                "incident.resolved", "incident.closed"
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=notification-service-it",
        "spring.config.import=",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "tracing.url=http://localhost:4317",
        "management.tracing.enabled=false",
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration," +
                "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration," +
                "org.springframework.boot.autoconfigure.mail.MailSenderValidatorAutoConfiguration," +
                "org.springframework.boot.actuate.autoconfigure.logging.OpenTelemetryLoggingAutoConfiguration"
})
@DirtiesContext
@DisplayName("Notification Integration Tests")
class NotificationIntegrationTest {

    @TestConfiguration
    static class TestKafkaProducerConfig {
        @Bean
        KafkaTemplate<String, String> testKafkaTemplate(EmbeddedKafkaBroker broker) {
            Map<String, Object> props = KafkaTestUtils.producerProps(broker);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            ProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(props);
            return new KafkaTemplate<>(pf);
        }
    }

    @Autowired KafkaTemplate<String, String> testKafkaTemplate;
    @Autowired KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    @Autowired EmbeddedKafkaBroker embeddedKafkaBroker;

    @MockitoBean JavaMailSender mailSender;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() throws Exception {
        // Wait for all consumer containers to have partitions assigned before sending messages
        for (MessageListenerContainer container : kafkaListenerEndpointRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
        }

        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    @DisplayName("user.registered event → consumer processes → email sent")
    void userRegistered_EventConsumed_EmailSent() throws Exception {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .email("user@example.com").firstName("John").lastName("Doe")
                .build();

        testKafkaTemplate.send("user.registered", objectMapper.writeValueAsString(event));

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                verify(mailSender, atLeastOnce()).send(any(MimeMessage.class)));
    }

    @Test
    @DisplayName("incident.created event → consumer processes → email sent")
    void incidentCreated_EventConsumed_EmailSent() throws Exception {
        IncidentCreatedEvent event = IncidentCreatedEvent.builder()
                .incidentId(UUID.randomUUID()).title("Network Down")
                .description("Main link dropped").priority(IncidentPriority.HIGH)
                .reportedBy(UUID.randomUUID()).reporterEmail("reporter@example.com")
                .build();

        testKafkaTemplate.send("incident.created", objectMapper.writeValueAsString(event));

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                verify(mailSender, atLeastOnce()).send(any(MimeMessage.class)));
    }
}
