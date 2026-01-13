package com.teletrack.reportingservice.listener;

import com.teletrack.commonutils.event.*;
import com.teletrack.reportingservice.document.IncidentReport;
import com.teletrack.reportingservice.repository.IncidentReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class IncidentEventListener {

    private final IncidentReportRepository reportRepository;

    @KafkaListener(topics = "incident.created", groupId = "reporting-service-group")
    @CacheEvict(value = {"incident-summary", "trend-report"}, allEntries = true)
    public void handleIncidentCreated(IncidentCreatedEvent event) {
        log.info("Received incident created event: {}", event.getIncidentId());

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
    public void handleIncidentAssigned(IncidentAssignedEvent event) {
        log.info("Received incident assigned event: {}", event.getIncidentId());

        Optional<IncidentReport> reportOpt = reportRepository.findByIncidentId(event.getIncidentId());
        if (reportOpt.isPresent()) {
            IncidentReport report = reportOpt.get();
            report.setAssignedTo(event.getAssignedTo());

            report.getActions().add(IncidentReport.IncidentAction.builder()
                    .action("ASSIGNED")
                    .performedBy(event.getAssignedBy())
                    .timestamp(event.getTimestamp())
                    .newValue(event.getAssignedTo().toString())
                    .build());

            reportRepository.save(report);
        }
    }

    @KafkaListener(topics = "incident.status.changed", groupId = "reporting-service-group")
    @CacheEvict(value = {"incident-summary", "trend-report"}, allEntries = true)
    public void handleStatusChanged(IncidentStatusChangedEvent event) {
        log.info("Received status changed event: {}", event.getIncidentId());

        Optional<IncidentReport> reportOpt = reportRepository.findByIncidentId(event.getIncidentId());
        if (reportOpt.isPresent()) {
            IncidentReport report = reportOpt.get();
            report.setStatus(event.getNewStatus().name());

            report.getActions().add(IncidentReport.IncidentAction.builder()
                    .action("STATUS_CHANGED")
                    .performedBy(event.getChangedBy())
                    .timestamp(event.getTimestamp())
                    .oldValue(event.getOldStatus().name())
                    .newValue(event.getNewStatus().name())
                    .build());

            reportRepository.save(report);
        }
    }

    @KafkaListener(topics = "incident.resolved", groupId = "reporting-service-group")
    @CacheEvict(value = {"incident-summary", "user-performance"}, allEntries = true)
    public void handleIncidentResolved(IncidentResolvedEvent event) {
        log.info("Received incident resolved event: {}", event.getIncidentId());

        Optional<IncidentReport> reportOpt = reportRepository.findByIncidentId(event.getIncidentId());
        if (reportOpt.isPresent()) {
            IncidentReport report = reportOpt.get();
            report.setResolvedAt(event.getResolvedAt());
            report.setResolutionTimeMinutes(event.getResolutionTimeMinutes());

            report.getActions().add(IncidentReport.IncidentAction.builder()
                    .action("RESOLVED")
                    .performedBy(event.getResolvedBy())
                    .timestamp(event.getTimestamp())
                    .newValue("RESOLVED")
                    .build());

            reportRepository.save(report);
        }
    }

    @KafkaListener(topics = "incident.closed", groupId = "reporting-service-group")
    @CacheEvict(value = {"incident-summary"}, allEntries = true)
    public void handleIncidentClosed(IncidentClosedEvent event) {
        log.info("Received incident closed event: {}", event.getIncidentId());

        Optional<IncidentReport> reportOpt = reportRepository.findByIncidentId(event.getIncidentId());
        if (reportOpt.isPresent()) {
            IncidentReport report = reportOpt.get();
            report.setClosedAt(event.getClosedAt());

            report.getActions().add(IncidentReport.IncidentAction.builder()
                    .action("CLOSED")
                    .performedBy(event.getClosedBy())
                    .timestamp(event.getTimestamp())
                    .newValue("CLOSED")
                    .build());

            reportRepository.save(report);
        }
    }
}