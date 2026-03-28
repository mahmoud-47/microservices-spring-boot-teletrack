package com.teletrack.notificationservice.unit.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.teletrack.commonutils.enums.IncidentPriority;
import com.teletrack.commonutils.enums.IncidentStatus;
import com.teletrack.commonutils.event.*;
import com.teletrack.notificationservice.consumer.NotificationConsumer;
import com.teletrack.notificationservice.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationConsumer Unit Tests")
class NotificationConsumerTest {

    @Mock EmailService emailService;

    NotificationConsumer consumer;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new NotificationConsumer(emailService, objectMapper);
    }

    // ─── handleUserRegistered ────────────────────────────────────────────────

    @Test
    @DisplayName("handleUserRegistered — sends welcome email")
    void handleUserRegistered_SendsEmail() throws Exception {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .email("user@example.com").firstName("John").lastName("Doe")
                .build();

        consumer.handleUserRegistered(objectMapper.writeValueAsString(event));

        verify(emailService).sendEmail(
                eq("user@example.com"),
                eq("Welcome to TeleTrack360"),
                eq("user-registered"),
                any(Context.class));
    }

    @Test
    @DisplayName("handleUserRegistered — bad JSON does not propagate")
    void handleUserRegistered_BadJson_DoesNotPropagate() {
        consumer.handleUserRegistered("{not valid json");
        verifyNoInteractions(emailService);
    }

    // ─── handleEmailVerification ─────────────────────────────────────────────

    @Test
    @DisplayName("handleEmailVerification — sends verification email")
    void handleEmailVerification_SendsEmail() throws Exception {
        EmailVerificationEvent event = EmailVerificationEvent.builder()
                .email("user@example.com").firstName("Jane")
                .verificationLink("http://verify/token")
                .build();

        consumer.handleEmailVerification(objectMapper.writeValueAsString(event));

        verify(emailService).sendEmail(
                eq("user@example.com"),
                eq("Verify Your Email - TeleTrack360"),
                eq("email-verification"),
                any(Context.class));
    }

    @Test
    @DisplayName("handleEmailVerification — bad JSON does not propagate")
    void handleEmailVerification_BadJson_DoesNotPropagate() {
        consumer.handleEmailVerification("invalid");
        verifyNoInteractions(emailService);
    }

    // ─── handleUserApproved ──────────────────────────────────────────────────

    @Test
    @DisplayName("handleUserApproved — sends approval email")
    void handleUserApproved_SendsEmail() throws Exception {
        UserApprovedEvent event = UserApprovedEvent.builder()
                .email("user@example.com").firstName("Bob").lastName("Smith")
                .build();

        consumer.handleUserApproved(objectMapper.writeValueAsString(event));

        verify(emailService).sendEmail(
                eq("user@example.com"),
                eq("Account Approved - TeleTrack360"),
                eq("user-approved"),
                any(Context.class));
    }

    @Test
    @DisplayName("handleUserApproved — bad JSON does not propagate")
    void handleUserApproved_BadJson_DoesNotPropagate() {
        consumer.handleUserApproved("invalid");
        verifyNoInteractions(emailService);
    }

    // ─── handleUserDeactivated ───────────────────────────────────────────────

    @Test
    @DisplayName("handleUserDeactivated — sends deactivation email")
    void handleUserDeactivated_SendsEmail() throws Exception {
        UserDeactivatedEvent event = UserDeactivatedEvent.builder()
                .email("user@example.com").reason("Policy violation")
                .build();

        consumer.handleUserDeactivated(objectMapper.writeValueAsString(event));

        verify(emailService).sendEmail(
                eq("user@example.com"),
                eq("Account Deactivated - TeleTrack360"),
                eq("user-deactivated"),
                any(Context.class));
    }

    @Test
    @DisplayName("handleUserDeactivated — bad JSON does not propagate")
    void handleUserDeactivated_BadJson_DoesNotPropagate() {
        consumer.handleUserDeactivated("invalid");
        verifyNoInteractions(emailService);
    }

    // ─── handleIncidentCreated ───────────────────────────────────────────────

    @Test
    @DisplayName("handleIncidentCreated — sends email to reporter")
    void handleIncidentCreated_SendsEmail() throws Exception {
        IncidentCreatedEvent event = IncidentCreatedEvent.builder()
                .incidentId(UUID.randomUUID()).title("DB Down")
                .description("Database unreachable").priority(IncidentPriority.CRITICAL)
                .reportedBy(UUID.randomUUID()).reporterEmail("reporter@example.com")
                .build();

        consumer.handleIncidentCreated(objectMapper.writeValueAsString(event));

        verify(emailService).sendEmail(
                eq("reporter@example.com"),
                anyString(),
                eq("incident-created"),
                any(Context.class));
    }

    @Test
    @DisplayName("handleIncidentCreated — bad JSON does not propagate")
    void handleIncidentCreated_BadJson_DoesNotPropagate() {
        consumer.handleIncidentCreated("bad");
        verifyNoInteractions(emailService);
    }

    // ─── handleIncidentAssigned ──────────────────────────────────────────────

    @Test
    @DisplayName("handleIncidentAssigned — sends email to both assignee and reporter")
    void handleIncidentAssigned_BothEmails_SendsTwice() throws Exception {
        IncidentAssignedEvent event = IncidentAssignedEvent.builder()
                .incidentId(UUID.randomUUID()).incidentTitle("DB Down")
                .assignedTo(UUID.randomUUID()).assignedBy(UUID.randomUUID())
                .assigneeEmail("assignee@example.com").reporterEmail("reporter@example.com")
                .build();

        consumer.handleIncidentAssigned(objectMapper.writeValueAsString(event));

        verify(emailService, times(2)).sendEmail(
                anyString(), anyString(), eq("incident-assigned"), any(Context.class));
    }

    @Test
    @DisplayName("handleIncidentAssigned — null assignee email: only reporter email sent")
    void handleIncidentAssigned_NullAssigneeEmail_OnlyReporterNotified() throws Exception {
        IncidentAssignedEvent event = IncidentAssignedEvent.builder()
                .incidentId(UUID.randomUUID()).incidentTitle("DB Down")
                .assignedTo(UUID.randomUUID()).assignedBy(UUID.randomUUID())
                .assigneeEmail(null).reporterEmail("reporter@example.com")
                .build();

        consumer.handleIncidentAssigned(objectMapper.writeValueAsString(event));

        verify(emailService, times(1)).sendEmail(
                eq("reporter@example.com"), anyString(), eq("incident-assigned"), any(Context.class));
    }

    @Test
    @DisplayName("handleIncidentAssigned — both emails null: no email sent")
    void handleIncidentAssigned_BothNull_NoEmail() throws Exception {
        IncidentAssignedEvent event = IncidentAssignedEvent.builder()
                .incidentId(UUID.randomUUID()).incidentTitle("DB Down")
                .assigneeEmail(null).reporterEmail(null)
                .build();

        consumer.handleIncidentAssigned(objectMapper.writeValueAsString(event));

        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("handleIncidentAssigned — bad JSON does not propagate")
    void handleIncidentAssigned_BadJson_DoesNotPropagate() {
        consumer.handleIncidentAssigned("bad");
        verifyNoInteractions(emailService);
    }

    // ─── handleIncidentStatusChanged ─────────────────────────────────────────

    @Test
    @DisplayName("handleIncidentStatusChanged — sends to both reporter and assignee")
    void handleIncidentStatusChanged_BothEmails() throws Exception {
        IncidentStatusChangedEvent event = IncidentStatusChangedEvent.builder()
                .incidentId(UUID.randomUUID()).incidentTitle("DB Down")
                .oldStatus(IncidentStatus.OPEN).newStatus(IncidentStatus.IN_PROGRESS)
                .changedBy(UUID.randomUUID())
                .reporterEmail("reporter@example.com").assigneeEmail("assignee@example.com")
                .build();

        consumer.handleIncidentStatusChanged(objectMapper.writeValueAsString(event));

        verify(emailService, times(2)).sendEmail(
                anyString(), anyString(), eq("incident-status-changed"), any(Context.class));
    }

    @Test
    @DisplayName("handleIncidentStatusChanged — null assignee email: only reporter notified")
    void handleIncidentStatusChanged_NullAssigneeEmail() throws Exception {
        IncidentStatusChangedEvent event = IncidentStatusChangedEvent.builder()
                .incidentId(UUID.randomUUID()).incidentTitle("DB Down")
                .oldStatus(IncidentStatus.OPEN).newStatus(IncidentStatus.IN_PROGRESS)
                .reporterEmail("reporter@example.com").assigneeEmail(null)
                .build();

        consumer.handleIncidentStatusChanged(objectMapper.writeValueAsString(event));

        verify(emailService, times(1)).sendEmail(
                eq("reporter@example.com"), anyString(), eq("incident-status-changed"), any(Context.class));
    }

    @Test
    @DisplayName("handleIncidentStatusChanged — bad JSON does not propagate")
    void handleIncidentStatusChanged_BadJson_DoesNotPropagate() {
        consumer.handleIncidentStatusChanged("bad");
        verifyNoInteractions(emailService);
    }

    // ─── handleIncidentResolved ──────────────────────────────────────────────

    @Test
    @DisplayName("handleIncidentResolved — sends resolved email to reporter")
    void handleIncidentResolved_SendsEmail() throws Exception {
        IncidentResolvedEvent event = IncidentResolvedEvent.builder()
                .incidentId(UUID.randomUUID()).incidentTitle("DB Down")
                .resolvedBy(UUID.randomUUID()).resolvedAt(LocalDateTime.now())
                .resolutionTimeMinutes(60L).reporterEmail("reporter@example.com")
                .build();

        consumer.handleIncidentResolved(objectMapper.writeValueAsString(event));

        verify(emailService).sendEmail(
                eq("reporter@example.com"),
                anyString(),
                eq("incident-resolved"),
                any(Context.class));
    }

    @Test
    @DisplayName("handleIncidentResolved — null reporter email: no email sent")
    void handleIncidentResolved_NullEmail_NoEmail() throws Exception {
        IncidentResolvedEvent event = IncidentResolvedEvent.builder()
                .incidentId(UUID.randomUUID()).incidentTitle("DB Down")
                .resolvedBy(UUID.randomUUID()).resolvedAt(LocalDateTime.now())
                .reporterEmail(null)
                .build();

        consumer.handleIncidentResolved(objectMapper.writeValueAsString(event));

        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("handleIncidentResolved — bad JSON does not propagate")
    void handleIncidentResolved_BadJson_DoesNotPropagate() {
        consumer.handleIncidentResolved("bad");
        verifyNoInteractions(emailService);
    }

    // ─── handleIncidentClosed ────────────────────────────────────────────────

    @Test
    @DisplayName("handleIncidentClosed — sends closed email to reporter")
    void handleIncidentClosed_SendsEmail() throws Exception {
        IncidentClosedEvent event = IncidentClosedEvent.builder()
                .incidentId(UUID.randomUUID()).incidentTitle("DB Down")
                .closedBy(UUID.randomUUID()).closedAt(LocalDateTime.now())
                .reporterEmail("reporter@example.com")
                .build();

        consumer.handleIncidentClosed(objectMapper.writeValueAsString(event));

        verify(emailService).sendEmail(
                eq("reporter@example.com"),
                anyString(),
                eq("incident-closed"),
                any(Context.class));
    }

    @Test
    @DisplayName("handleIncidentClosed — null reporter email: no email sent")
    void handleIncidentClosed_NullEmail_NoEmail() throws Exception {
        IncidentClosedEvent event = IncidentClosedEvent.builder()
                .incidentId(UUID.randomUUID()).incidentTitle("DB Down")
                .closedBy(UUID.randomUUID()).closedAt(LocalDateTime.now())
                .reporterEmail(null)
                .build();

        consumer.handleIncidentClosed(objectMapper.writeValueAsString(event));

        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("handleIncidentClosed — bad JSON does not propagate")
    void handleIncidentClosed_BadJson_DoesNotPropagate() {
        consumer.handleIncidentClosed("bad");
        verifyNoInteractions(emailService);
    }
}
