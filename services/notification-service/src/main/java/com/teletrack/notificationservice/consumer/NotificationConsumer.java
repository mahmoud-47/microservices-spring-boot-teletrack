package com.teletrack.notificationservice.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teletrack.commonutils.event.*;
import com.teletrack.notificationservice.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user.registered", groupId = "${spring.kafka.consumer.group-id}")
    public void handleUserRegistered(String message) {
        UserRegisteredEvent event = parse(message, UserRegisteredEvent.class);
        if (event == null) return;
        log.info("Processing user.registered event for: {}", event.getEmail());
        Context context = new Context();
        context.setVariable("firstName", event.getFirstName());
        context.setVariable("lastName", event.getLastName());
        emailService.sendEmail(event.getEmail(), "Welcome to TeleTrack360", "user-registered", context);
    }

    @KafkaListener(topics = "email.verification", groupId = "${spring.kafka.consumer.group-id}")
    public void handleEmailVerification(String message) {
        EmailVerificationEvent event = parse(message, EmailVerificationEvent.class);
        if (event == null) return;
        log.info("Processing email.verification event for: {}", event.getEmail());
        Context context = new Context();
        context.setVariable("firstName", event.getFirstName());
        context.setVariable("verificationLink", event.getVerificationLink());
        emailService.sendEmail(event.getEmail(), "Verify Your Email - TeleTrack360", "email-verification", context);
    }

    @KafkaListener(topics = "user.approved", groupId = "${spring.kafka.consumer.group-id}")
    public void handleUserApproved(String message) {
        UserApprovedEvent event = parse(message, UserApprovedEvent.class);
        if (event == null) return;
        log.info("Processing user.approved event for: {}", event.getEmail());
        Context context = new Context();
        context.setVariable("firstName", event.getFirstName());
        context.setVariable("lastName", event.getLastName());
        emailService.sendEmail(event.getEmail(), "Account Approved - TeleTrack360", "user-approved", context);
    }

    @KafkaListener(topics = "user.deactivated", groupId = "${spring.kafka.consumer.group-id}")
    public void handleUserDeactivated(String message) {
        UserDeactivatedEvent event = parse(message, UserDeactivatedEvent.class);
        if (event == null) return;
        log.info("Processing user.deactivated event for: {}", event.getEmail());
        Context context = new Context();
        context.setVariable("reason", event.getReason());
        emailService.sendEmail(event.getEmail(), "Account Deactivated - TeleTrack360", "user-deactivated", context);
    }

    @KafkaListener(topics = "incident.created", groupId = "${spring.kafka.consumer.group-id}")
    public void handleIncidentCreated(String message) {
        IncidentCreatedEvent event = parse(message, IncidentCreatedEvent.class);
        if (event == null) return;
        log.info("Processing incident.created event for incident: {}", event.getIncidentId());
        Context context = new Context();
        context.setVariable("incidentId", event.getIncidentId());
        context.setVariable("title", event.getTitle());
        context.setVariable("description", event.getDescription());
        context.setVariable("priority", event.getPriority());
        emailService.sendEmail(event.getReporterEmail(), "Incident Created - " + event.getIncidentId(), "incident-created", context);
    }

    @KafkaListener(topics = "incident.assigned", groupId = "${spring.kafka.consumer.group-id}")
    public void handleIncidentAssigned(String message) {
        IncidentAssignedEvent event = parse(message, IncidentAssignedEvent.class);
        if (event == null) return;
        log.info("Processing incident.assigned event for incident: {}", event.getIncidentId());
        Context context = new Context();
        context.setVariable("incidentId", event.getIncidentId());
        context.setVariable("title", event.getIncidentTitle());
        if (event.getAssigneeEmail() != null) {
            emailService.sendEmail(event.getAssigneeEmail(), "Incident Assigned - " + event.getIncidentId(), "incident-assigned", context);
        }
        if (event.getReporterEmail() != null) {
            emailService.sendEmail(event.getReporterEmail(), "Incident Assigned - " + event.getIncidentId(), "incident-assigned", context);
        }
    }

    @KafkaListener(topics = "incident.status.changed", groupId = "${spring.kafka.consumer.group-id}")
    public void handleIncidentStatusChanged(String message) {
        IncidentStatusChangedEvent event = parse(message, IncidentStatusChangedEvent.class);
        if (event == null) return;
        log.info("Processing incident.status.changed event for incident: {}", event.getIncidentId());
        Context context = new Context();
        context.setVariable("incidentId", event.getIncidentId());
        context.setVariable("oldStatus", event.getOldStatus());
        context.setVariable("newStatus", event.getNewStatus());
        if (event.getReporterEmail() != null) {
            emailService.sendEmail(event.getReporterEmail(), "Incident Status Updated - " + event.getIncidentId(), "incident-status-changed", context);
        }
        if (event.getAssigneeEmail() != null) {
            emailService.sendEmail(event.getAssigneeEmail(), "Incident Status Updated - " + event.getIncidentId(), "incident-status-changed", context);
        }
    }

    @KafkaListener(topics = "incident.resolved", groupId = "${spring.kafka.consumer.group-id}")
    public void handleIncidentResolved(String message) {
        IncidentResolvedEvent event = parse(message, IncidentResolvedEvent.class);
        if (event == null) return;
        log.info("Processing incident.resolved event for incident: {}", event.getIncidentId());
        Context context = new Context();
        context.setVariable("incidentId", event.getIncidentId());
        context.setVariable("title", event.getIncidentTitle());
        if (event.getReporterEmail() != null) {
            emailService.sendEmail(event.getReporterEmail(), "Incident Resolved - " + event.getIncidentId(), "incident-resolved", context);
        }
    }

    @KafkaListener(topics = "incident.closed", groupId = "${spring.kafka.consumer.group-id}")
    public void handleIncidentClosed(String message) {
        IncidentClosedEvent event = parse(message, IncidentClosedEvent.class);
        if (event == null) return;
        log.info("Processing incident.closed event for incident: {}", event.getIncidentId());
        Context context = new Context();
        context.setVariable("incidentId", event.getIncidentId());
        context.setVariable("title", event.getIncidentTitle());
        if (event.getReporterEmail() != null) {
            emailService.sendEmail(event.getReporterEmail(), "Incident Closed - " + event.getIncidentId(), "incident-closed", context);
        }
    }

    private <T> T parse(String message, Class<T> type) {
        try {
            return objectMapper.readValue(message, type);
        } catch (JsonProcessingException e) {
            log.error("Unparseable {} message, discarding: {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }
}
