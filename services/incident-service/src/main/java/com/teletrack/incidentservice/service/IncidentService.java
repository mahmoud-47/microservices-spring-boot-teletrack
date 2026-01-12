package com.teletrack.incidentservice.service;

import com.teletrack.commonutils.dto.request.AssignIncidentRequest;
import com.teletrack.commonutils.dto.request.CreateIncidentRequest;
import com.teletrack.commonutils.dto.request.UpdateIncidentRequest;
import com.teletrack.commonutils.dto.response.IncidentResponse;
import com.teletrack.commonutils.enums.IncidentStatus;
import com.teletrack.commonutils.event.IncidentAssignedEvent;
import com.teletrack.commonutils.event.IncidentCreatedEvent;
import com.teletrack.commonutils.event.IncidentResolvedEvent;
import com.teletrack.commonutils.event.IncidentStatusChangedEvent;
import com.teletrack.commonutils.exception.ResourceNotFoundException;
import com.teletrack.incidentservice.entity.Incident;
import com.teletrack.incidentservice.entity.IncidentHistory;
import com.teletrack.incidentservice.mapper.IncidentMapper;
import com.teletrack.incidentservice.repository.IncidentHistoryRepository;
import com.teletrack.incidentservice.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentService {

//    1/0; need to check user before assigning

    private final IncidentRepository incidentRepository;
    private final IncidentHistoryRepository incidentHistoryRepository;
    private final IncidentMapper incidentMapper;
    private final EventPublisher eventPublisher;

    @Transactional
    public IncidentResponse createIncident(CreateIncidentRequest request, UUID userId) {
        log.info("Creating incident for user: {}", userId);

        Incident incident = Incident.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority())
                .status(IncidentStatus.OPEN)
                .reportedBy(userId)
                .build();

        incident = incidentRepository.save(incident);

        // Create audit history
        createHistoryEntry(incident.getId(), userId, "INCIDENT_CREATED",
                null, "Created with status: OPEN");

        // Publish event
        IncidentCreatedEvent event = IncidentCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("INCIDENT_CREATED")
                .timestamp(LocalDateTime.now())
                .correlationId(UUID.randomUUID().toString())
                .incidentId(incident.getId())
                .title(incident.getTitle())
                .description(incident.getDescription())
                .priority(incident.getPriority())
                .reportedBy(userId)
                .build();

        eventPublisher.publishEvent("incident.created", event);

        return toIncidentResponseWithHistory(incident);
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncidentById(UUID id) {
        log.info("Fetching incident: {}", id);

        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));

        return toIncidentResponseWithHistory(incident);
    }

    @Transactional(readOnly = true)
    public Page<IncidentResponse> getAllIncidents(Pageable pageable) {
        log.info("Fetching all incidents with pagination");

        return incidentRepository.findAll(pageable)
                .map(this::toIncidentResponseWithHistory);
    }

    @Transactional
    public IncidentResponse updateIncident(UUID id, UpdateIncidentRequest request, UUID userId) {
        log.info("Updating incident: {} by user: {}", id, userId);

        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));

        boolean updated = false;

        if (request.getTitle() != null && !request.getTitle().equals(incident.getTitle())) {
            createHistoryEntry(id, userId, "TITLE_UPDATED",
                    incident.getTitle(), request.getTitle());
            incident.setTitle(request.getTitle());
            updated = true;
        }

        if (request.getDescription() != null && !request.getDescription().equals(incident.getDescription())) {
            createHistoryEntry(id, userId, "DESCRIPTION_UPDATED",
                    "Description changed", "Description changed");
            incident.setDescription(request.getDescription());
            updated = true;
        }

        if (request.getPriority() != null && request.getPriority() != incident.getPriority()) {
            createHistoryEntry(id, userId, "PRIORITY_UPDATED",
                    incident.getPriority().name(), request.getPriority().name());
            incident.setPriority(request.getPriority());
            updated = true;
        }

        if (request.getStatus() != null && request.getStatus() != incident.getStatus()) {
            changeStatusInternal(incident, request.getStatus(), userId);
            updated = true;
        }

        if (updated) {
            incident = incidentRepository.save(incident);
        }

        return toIncidentResponseWithHistory(incident);
    }

    @Transactional
    public IncidentResponse assignIncident(UUID id, AssignIncidentRequest request, UUID userId) {
        log.info("Assigning incident: {} to user: {} by: {}", id, request.getAssignedTo(), userId);

        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));

        UUID oldAssignee = incident.getAssignedTo();
        incident.setAssignedTo(request.getAssignedTo());
        incident = incidentRepository.save(incident);

        // Create audit history
        createHistoryEntry(id, userId, "INCIDENT_ASSIGNED",
                oldAssignee != null ? oldAssignee.toString() : "Unassigned",
                request.getAssignedTo().toString());

        // Publish event
        IncidentAssignedEvent event = IncidentAssignedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("INCIDENT_ASSIGNED")
                .timestamp(LocalDateTime.now())
                .correlationId(UUID.randomUUID().toString())
                .incidentId(incident.getId())
                .incidentTitle(incident.getTitle())
                .assignedTo(request.getAssignedTo())
                .assignedBy(userId)
                .build();

        eventPublisher.publishEvent("incident.assigned", event);

        return toIncidentResponseWithHistory(incident);
    }

    @Transactional
    public IncidentResponse changeStatus(UUID id, IncidentStatus newStatus, UUID userId) {
        log.info("Changing incident: {} status to: {} by user: {}", id, newStatus, userId);

        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));

        changeStatusInternal(incident, newStatus, userId);
        incident = incidentRepository.save(incident);

        return toIncidentResponseWithHistory(incident);
    }

    @Transactional(readOnly = true)
    public Page<IncidentResponse> getIncidentsByStatus(IncidentStatus status, Pageable pageable) {
        log.info("Fetching incidents with status: {}", status);

        return incidentRepository.findByStatus(status, pageable)
                .map(this::toIncidentResponseWithHistory);
    }

    @Transactional(readOnly = true)
    public Page<IncidentResponse> getIncidentsByAssignedTo(UUID userId, Pageable pageable) {
        log.info("Fetching incidents assigned to user: {}", userId);

        return incidentRepository.findByAssignedTo(userId, pageable)
                .map(this::toIncidentResponseWithHistory);
    }

    @Transactional(readOnly = true)
    public Page<IncidentResponse> getIncidentsByReportedBy(UUID userId, Pageable pageable) {
        log.info("Fetching incidents reported by user: {}", userId);

        return incidentRepository.findByReportedBy(userId, pageable)
                .map(this::toIncidentResponseWithHistory);
    }

    // Helper methods

    private void changeStatusInternal(Incident incident, IncidentStatus newStatus, UUID userId) {
        IncidentStatus oldStatus = incident.getStatus();

        if (oldStatus == newStatus) {
            return;
        }

        incident.setStatus(newStatus);

        // Set timestamps based on status
        if (newStatus == IncidentStatus.RESOLVED) {
            incident.setResolvedAt(LocalDateTime.now());

            // Publish resolved event
            long resolutionTimeMinutes = ChronoUnit.MINUTES.between(
                    incident.getCreatedAt(), incident.getResolvedAt());

            IncidentResolvedEvent event = IncidentResolvedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("INCIDENT_RESOLVED")
                    .timestamp(LocalDateTime.now())
                    .correlationId(UUID.randomUUID().toString())
                    .incidentId(incident.getId())
                    .incidentTitle(incident.getTitle())
                    .resolvedBy(userId)
                    .resolvedAt(incident.getResolvedAt())
                    .resolutionTimeMinutes(resolutionTimeMinutes)
                    .build();

            eventPublisher.publishEvent("incident.resolved", event);
        } else if (newStatus == IncidentStatus.CLOSED) {
            incident.setClosedAt(LocalDateTime.now());
        }

        // Create audit history
        createHistoryEntry(incident.getId(), userId, "STATUS_CHANGED",
                oldStatus.name(), newStatus.name());

        // Publish status changed event
        IncidentStatusChangedEvent event = IncidentStatusChangedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("INCIDENT_STATUS_CHANGED")
                .timestamp(LocalDateTime.now())
                .correlationId(UUID.randomUUID().toString())
                .incidentId(incident.getId())
                .incidentTitle(incident.getTitle())
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedBy(userId)
                .build();

        eventPublisher.publishEvent("incident.status.changed", event);
    }

    private void createHistoryEntry(UUID incidentId, UUID userId, String action,
                                    String oldValue, String newValue) {
        IncidentHistory history = IncidentHistory.builder()
                .incidentId(incidentId)
                .userId(userId)
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .timestamp(LocalDateTime.now())
                .build();

        incidentHistoryRepository.save(history);
    }

    private IncidentResponse toIncidentResponseWithHistory(Incident incident) {
        IncidentResponse response = incidentMapper.toIncidentResponse(incident);

        List<IncidentHistory> history = incidentHistoryRepository
                .findByIncidentIdOrderByTimestampDesc(incident.getId());

        response.setHistory(incidentMapper.toIncidentHistoryResponseList(history));

        return response;
    }
}