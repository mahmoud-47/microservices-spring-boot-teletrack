package com.teletrack.notificationservice.consumer;

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
        try {
            UserRegisteredEvent event = objectMapper.readValue(message, UserRegisteredEvent.class);
            log.info("Processing user.registered event for: {}", event.getEmail());

            Context context = new Context();
            context.setVariable("firstName", event.getFirstName());
            context.setVariable("lastName", event.getLastName());

            emailService.sendEmail(
                    event.getEmail(),
                    "Welcome to TeleTrack360",
                    "user-registered",
                    context
            );
        } catch (Exception e) {
            log.error("Error processing user.registered event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "email.verification", groupId = "${spring.kafka.consumer.group-id}")
    public void handleEmailVerification(String message) {
        try {
            EmailVerificationEvent event = objectMapper.readValue(message, EmailVerificationEvent.class);
            log.info("Processing email.verification event for: {}", event.getEmail());

            Context context = new Context();
            context.setVariable("firstName", event.getFirstName());
            context.setVariable("verificationLink", event.getVerificationLink());

            emailService.sendEmail(
                    event.getEmail(),
                    "Verify Your Email - TeleTrack360",
                    "email-verification",
                    context
            );
        } catch (Exception e) {
            log.error("Error processing email.verification event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "user.approved", groupId = "${spring.kafka.consumer.group-id}")
    public void handleUserApproved(String message) {
        try {
            UserApprovedEvent event = objectMapper.readValue(message, UserApprovedEvent.class);
            log.info("Processing user.approved event for: {}", event.getEmail());

            Context context = new Context();
            context.setVariable("firstName", event.getFirstName());
            context.setVariable("lastName", event.getLastName());

            emailService.sendEmail(
                    event.getEmail(),
                    "Account Approved - TeleTrack360",
                    "user-approved",
                    context
            );
        } catch (Exception e) {
            log.error("Error processing user.approved event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "user.deactivated", groupId = "${spring.kafka.consumer.group-id}")
    public void handleUserDeactivated(String message) {
        try {
            UserDeactivatedEvent event = objectMapper.readValue(message, UserDeactivatedEvent.class);
            log.info("Processing user.deactivated event for: {}", event.getEmail());

            Context context = new Context();
            context.setVariable("reason", event.getReason());

            emailService.sendEmail(
                    event.getEmail(),
                    "Account Deactivated - TeleTrack360",
                    "user-deactivated",
                    context
            );
        } catch (Exception e) {
            log.error("Error processing user.deactivated event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "incident.created", groupId = "${spring.kafka.consumer.group-id}")
    public void handleIncidentCreated(String message) {
        try {
            IncidentCreatedEvent event = objectMapper.readValue(message, IncidentCreatedEvent.class);
            log.info("Processing incident.created event for incident: {}", event.getIncidentId());

            Context context = new Context();
            context.setVariable("incidentId", event.getIncidentId());
            context.setVariable("title", event.getTitle());
            context.setVariable("description", event.getDescription());
            context.setVariable("priority", event.getPriority());

            emailService.sendEmail(
                    event.getReporterEmail(),
                    "Incident Created - " + event.getIncidentId(),
                    "incident-created",
                    context
            );
        } catch (Exception e) {
            log.error("Error processing incident.created event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "incident.assigned", groupId = "${spring.kafka.consumer.group-id}")
    public void handleIncidentAssigned(String message) {
        try {
            IncidentAssignedEvent event = objectMapper.readValue(message, IncidentAssignedEvent.class);
            log.info("Processing incident.assigned event for incident: {}", event.getIncidentId());

            Context context = new Context();
            context.setVariable("incidentId", event.getIncidentId());
            context.setVariable("title", event.getIncidentTitle());

            if (event.getAssigneeEmail() != null) {
                emailService.sendEmail(
                        event.getAssigneeEmail(),
                        "Incident Assigned - " + event.getIncidentId(),
                        "incident-assigned",
                        context
                );
            }

            if (event.getReporterEmail() != null) {
                emailService.sendEmail(
                        event.getReporterEmail(),
                        "Incident Assigned - " + event.getIncidentId(),
                        "incident-assigned",
                        context
                );
            }
        } catch (Exception e) {
            log.error("Error processing incident.assigned event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "incident.status.changed", groupId = "${spring.kafka.consumer.group-id}")
    public void handleIncidentStatusChanged(String message) {
        try {
            IncidentStatusChangedEvent event = objectMapper.readValue(message, IncidentStatusChangedEvent.class);
            log.info("Processing incident.status.changed event for incident: {}", event.getIncidentId());

            Context context = new Context();
            context.setVariable("incidentId", event.getIncidentId());
            context.setVariable("oldStatus", event.getOldStatus());
            context.setVariable("newStatus", event.getNewStatus());

            if (event.getReporterEmail() != null) {
                emailService.sendEmail(
                        event.getReporterEmail(),
                        "Incident Status Updated - " + event.getIncidentId(),
                        "incident-status-changed",
                        context
                );
            }

            if (event.getAssigneeEmail() != null) {
                emailService.sendEmail(
                        event.getAssigneeEmail(),
                        "Incident Status Updated - " + event.getIncidentId(),
                        "incident-status-changed",
                        context
                );
            }
        } catch (Exception e) {
            log.error("Error processing incident.status.changed event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "incident.resolved", groupId = "${spring.kafka.consumer.group-id}")
    public void handleIncidentResolved(String message) {
        try {
            IncidentResolvedEvent event = objectMapper.readValue(message, IncidentResolvedEvent.class);
            log.info("Processing incident.resolved event for incident: {}", event.getIncidentId());

            Context context = new Context();
            context.setVariable("incidentId", event.getIncidentId());
            context.setVariable("title", event.getIncidentTitle());

            if (event.getReporterEmail() != null) {
                emailService.sendEmail(
                        event.getReporterEmail(),
                        "Incident Resolved - " + event.getIncidentId(),
                        "incident-resolved",
                        context
                );
            }
        } catch (Exception e) {
            log.error("Error processing incident.resolved event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "incident.closed", groupId = "${spring.kafka.consumer.group-id}")
    public void handleIncidentClosed(String message) {
        try {
            IncidentClosedEvent event = objectMapper.readValue(message, IncidentClosedEvent.class);
            log.info("Processing incident.closed event for incident: {}", event.getIncidentId());

            Context context = new Context();
            context.setVariable("incidentId", event.getIncidentId());
            context.setVariable("title", event.getIncidentTitle());

            if (event.getReporterEmail() != null) {
                emailService.sendEmail(
                        event.getReporterEmail(),
                        "Incident Closed - " + event.getIncidentId(),
                        "incident-closed",
                        context
                );
            }
        } catch (Exception e) {
            log.error("Error processing incident.closed event: {}", e.getMessage());
        }
    }
}