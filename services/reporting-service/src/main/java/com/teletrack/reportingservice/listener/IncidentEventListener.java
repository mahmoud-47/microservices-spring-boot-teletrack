package com.teletrack.reportingservice.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teletrack.commonutils.event.*;
import com.teletrack.reportingservice.document.IncidentReport;
import com.teletrack.reportingservice.repository.IncidentReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class IncidentEventListener {

    private final IncidentReportRepository reportRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "incident.created", groupId = "reporting-service-group")
    @CacheEvict(value = {"incident-summary", "trend-report"}, allEntries = true)
    public void handleIncidentCreated(String message) throws JsonProcessingException {
        IncidentCreatedEvent event = parse(message, IncidentCreatedEvent.class);
        if (event == null) return;

        if (reportRepository.existsByIncidentId(event.getIncidentId())) {
            log.warn("Duplicate INCIDENT_CREATED for {}, skipping", event.getIncidentId());
            return;
        }

        IncidentReport report = IncidentReport.builder()
                .incidentId(event.getIncidentId())
                .title(event.getTitle())
                .status("OPEN")
                .priority(event.getPriority().name())
                .reportedBy(event.getReportedBy())
                .createdAt(event.getTimestamp())
                .build();

        report.getActions().add(IncidentReport.IncidentAction.builder()
                .action("CREATED")
                .performedBy(event.getReportedBy())
                .timestamp(event.getTimestamp())
                .newValue("OPEN")
                .build());

        reportRepository.save(report);
        log.info("Incident report created for: {}", event.getIncidentId());
    }

    @KafkaListener(topics = "incident.assigned", groupId = "reporting-service-group")
    @CacheEvict(value = {"incident-summary", "user-performance"}, allEntries = true)
    public void handleIncidentAssigned(String message) throws JsonProcessingException {
        IncidentAssignedEvent event = parse(message, IncidentAssignedEvent.class);
        if (event == null) return;

        Optional<IncidentReport> reportOpt = reportRepository.findByIncidentId(event.getIncidentId());
        if (reportOpt.isEmpty()) {
            log.warn("Report not found for incident {}, skipping INCIDENT_ASSIGNED", event.getIncidentId());
            return;
        }

        IncidentReport report = reportOpt.get();

        boolean isDuplicate = report.getActions().stream()
                .filter(a -> "ASSIGNED".equals(a.getAction()))
                .filter(a -> event.getAssignedTo().toString().equals(a.getNewValue()))
                .anyMatch(a -> a.getTimestamp().isAfter(LocalDateTime.now().minusSeconds(60)));

        if (isDuplicate) {
            log.warn("Duplicate INCIDENT_ASSIGNED for {} to {} within 60s, skipping",
                    event.getIncidentId(), event.getAssignedTo());
            return;
        }

        report.setAssignedTo(event.getAssignedTo());
        report.getActions().add(IncidentReport.IncidentAction.builder()
                .action("ASSIGNED")
                .performedBy(event.getAssignedBy())
                .timestamp(event.getTimestamp())
                .newValue(event.getAssignedTo().toString())
                .build());

        reportRepository.save(report);
        log.info("Incident {} assigned to {}", event.getIncidentId(), event.getAssignedTo());
    }

    @KafkaListener(topics = "incident.status.changed", groupId = "reporting-service-group")
    @CacheEvict(value = {"incident-summary", "trend-report"}, allEntries = true)
    public void handleStatusChanged(String message) throws JsonProcessingException {
        IncidentStatusChangedEvent event = parse(message, IncidentStatusChangedEvent.class);
        if (event == null) return;

        Optional<IncidentReport> reportOpt = reportRepository.findByIncidentId(event.getIncidentId());
        if (reportOpt.isEmpty()) {
            log.warn("Report not found for incident {}, skipping STATUS_CHANGED", event.getIncidentId());
            return;
        }

        IncidentReport report = reportOpt.get();

        boolean isDuplicate = report.getActions().stream()
                .filter(a -> "STATUS_CHANGED".equals(a.getAction()))
                .filter(a -> event.getNewStatus().name().equals(a.getNewValue()))
                .anyMatch(a -> a.getTimestamp().isAfter(LocalDateTime.now().minusSeconds(60)));

        if (isDuplicate) {
            log.warn("Duplicate STATUS_CHANGED for {} to {} within 60s, skipping",
                    event.getIncidentId(), event.getNewStatus());
            return;
        }

        report.setStatus(event.getNewStatus().name());
        report.getActions().add(IncidentReport.IncidentAction.builder()
                .action("STATUS_CHANGED")
                .performedBy(event.getChangedBy())
                .timestamp(event.getTimestamp())
                .oldValue(event.getOldStatus().name())
                .newValue(event.getNewStatus().name())
                .build());

        reportRepository.save(report);
        log.info("Incident {} status changed to {}", event.getIncidentId(), event.getNewStatus());
    }

    @KafkaListener(topics = "incident.resolved", groupId = "reporting-service-group")
    @CacheEvict(value = {"incident-summary", "user-performance"}, allEntries = true)
    public void handleIncidentResolved(String message) throws JsonProcessingException {
        IncidentResolvedEvent event = parse(message, IncidentResolvedEvent.class);
        if (event == null) return;

        Optional<IncidentReport> reportOpt = reportRepository.findByIncidentId(event.getIncidentId());
        if (reportOpt.isEmpty()) {
            log.warn("Report not found for incident {}, skipping INCIDENT_RESOLVED", event.getIncidentId());
            return;
        }

        IncidentReport report = reportOpt.get();

        if (report.getResolvedAt() != null) {
            log.warn("Incident {} already resolved at {}, skipping duplicate",
                    event.getIncidentId(), report.getResolvedAt());
            return;
        }

        report.setResolvedAt(event.getResolvedAt());
        report.setResolutionTimeMinutes(event.getResolutionTimeMinutes());
        report.getActions().add(IncidentReport.IncidentAction.builder()
                .action("RESOLVED")
                .performedBy(event.getResolvedBy())
                .timestamp(event.getTimestamp())
                .newValue("RESOLVED")
                .build());

        reportRepository.save(report);
        log.info("Incident {} marked resolved", event.getIncidentId());
    }

    @KafkaListener(topics = "incident.closed", groupId = "reporting-service-group")
    @CacheEvict(value = {"incident-summary"}, allEntries = true)
    public void handleIncidentClosed(String message) throws JsonProcessingException {
        IncidentClosedEvent event = parse(message, IncidentClosedEvent.class);
        if (event == null) return;

        Optional<IncidentReport> reportOpt = reportRepository.findByIncidentId(event.getIncidentId());
        if (reportOpt.isEmpty()) {
            log.warn("Report not found for incident {}, skipping INCIDENT_CLOSED", event.getIncidentId());
            return;
        }

        IncidentReport report = reportOpt.get();

        if (report.getClosedAt() != null) {
            log.warn("Incident {} already closed at {}, skipping duplicate",
                    event.getIncidentId(), report.getClosedAt());
            return;
        }

        report.setClosedAt(event.getClosedAt());
        report.getActions().add(IncidentReport.IncidentAction.builder()
                .action("CLOSED")
                .performedBy(event.getClosedBy())
                .timestamp(event.getTimestamp())
                .newValue("CLOSED")
                .build());

        reportRepository.save(report);
        log.info("Incident {} marked closed", event.getIncidentId());
    }

    /**
     * Parses a Kafka message into the target type.
     * Returns null and logs a warning for malformed messages — these are not retried
     * because bad JSON will never succeed regardless of how many times we retry.
     */
    private <T> T parse(String message, Class<T> type) {
        try {
            return objectMapper.readValue(message, type);
        } catch (JsonProcessingException e) {
            log.error("Unparseable {} message, discarding: {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }
}
