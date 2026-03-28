package com.teletrack.reportingservice.unit.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.teletrack.commonutils.enums.IncidentPriority;
import com.teletrack.commonutils.enums.IncidentStatus;
import com.teletrack.commonutils.event.*;
import com.teletrack.reportingservice.document.IncidentReport;
import com.teletrack.reportingservice.listener.IncidentEventListener;
import com.teletrack.reportingservice.repository.IncidentReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("IncidentEventListener Unit Tests")
class IncidentEventListenerTest {

    @Mock
    private IncidentReportRepository reportRepository;

    private IncidentEventListener listener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        listener = new IncidentEventListener(reportRepository, objectMapper);
    }

    // ─── handleIncidentCreated ─────────────────────────────────────────────────

    @Test
    @DisplayName("handleIncidentCreated: saves new IncidentReport with OPEN status")
    void handleIncidentCreated_SavesReport() throws Exception {
        UUID incidentId = UUID.randomUUID();
        UUID reportedBy = UUID.randomUUID();

        IncidentCreatedEvent event = IncidentCreatedEvent.builder()
                .incidentId(incidentId)
                .title("Network outage")
                .priority(IncidentPriority.HIGH)
                .reportedBy(reportedBy)
                .timestamp(LocalDateTime.now())
                .build();

        String json = objectMapper.writeValueAsString(event);

        listener.handleIncidentCreated(json);

        ArgumentCaptor<IncidentReport> captor = ArgumentCaptor.forClass(IncidentReport.class);
        verify(reportRepository).save(captor.capture());
        IncidentReport saved = captor.getValue();

        assertThat(saved.getIncidentId()).isEqualTo(incidentId);
        assertThat(saved.getTitle()).isEqualTo("Network outage");
        assertThat(saved.getStatus()).isEqualTo("OPEN");
        assertThat(saved.getPriority()).isEqualTo("HIGH");
        assertThat(saved.getReportedBy()).isEqualTo(reportedBy);
        assertThat(saved.getActions()).hasSize(1);
        assertThat(saved.getActions().get(0).getAction()).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("handleIncidentCreated: malformed JSON does not propagate exception")
    void handleIncidentCreated_BadJson_NoException() {
        listener.handleIncidentCreated("not-valid-json");

        verify(reportRepository, never()).save(any());
    }

    // ─── handleIncidentAssigned ────────────────────────────────────────────────

    @Test
    @DisplayName("handleIncidentAssigned: updates assignedTo and adds action")
    void handleIncidentAssigned_UpdatesReport() throws Exception {
        UUID incidentId = UUID.randomUUID();
        UUID assignedTo = UUID.randomUUID();
        UUID assignedBy = UUID.randomUUID();

        IncidentReport existing = IncidentReport.builder()
                .incidentId(incidentId)
                .status("OPEN")
                .build();

        when(reportRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(existing));

        IncidentAssignedEvent event = IncidentAssignedEvent.builder()
                .incidentId(incidentId)
                .assignedTo(assignedTo)
                .assignedBy(assignedBy)
                .timestamp(LocalDateTime.now())
                .build();

        listener.handleIncidentAssigned(objectMapper.writeValueAsString(event));

        verify(reportRepository).save(existing);
        assertThat(existing.getAssignedTo()).isEqualTo(assignedTo);
        assertThat(existing.getActions()).hasSize(1);
        assertThat(existing.getActions().get(0).getAction()).isEqualTo("ASSIGNED");
    }

    @Test
    @DisplayName("handleIncidentAssigned: incident not found — no save")
    void handleIncidentAssigned_NotFound_NoSave() throws Exception {
        UUID incidentId = UUID.randomUUID();
        when(reportRepository.findByIncidentId(incidentId)).thenReturn(Optional.empty());

        IncidentAssignedEvent event = IncidentAssignedEvent.builder()
                .incidentId(incidentId)
                .assignedTo(UUID.randomUUID())
                .assignedBy(UUID.randomUUID())
                .timestamp(LocalDateTime.now())
                .build();

        listener.handleIncidentAssigned(objectMapper.writeValueAsString(event));

        verify(reportRepository, never()).save(any());
    }

    // ─── handleStatusChanged ──────────────────────────────────────────────────

    @Test
    @DisplayName("handleStatusChanged: updates status and adds action with old/new values")
    void handleStatusChanged_UpdatesStatus() throws Exception {
        UUID incidentId = UUID.randomUUID();

        IncidentReport existing = IncidentReport.builder()
                .incidentId(incidentId)
                .status("OPEN")
                .build();

        when(reportRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(existing));

        IncidentStatusChangedEvent event = IncidentStatusChangedEvent.builder()
                .incidentId(incidentId)
                .oldStatus(IncidentStatus.OPEN)
                .newStatus(IncidentStatus.IN_PROGRESS)
                .changedBy(UUID.randomUUID())
                .timestamp(LocalDateTime.now())
                .build();

        listener.handleStatusChanged(objectMapper.writeValueAsString(event));

        verify(reportRepository).save(existing);
        assertThat(existing.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(existing.getActions().get(0).getOldValue()).isEqualTo("OPEN");
        assertThat(existing.getActions().get(0).getNewValue()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("handleStatusChanged: incident not found — no save")
    void handleStatusChanged_NotFound_NoSave() throws Exception {
        UUID incidentId = UUID.randomUUID();
        when(reportRepository.findByIncidentId(incidentId)).thenReturn(Optional.empty());

        IncidentStatusChangedEvent event = IncidentStatusChangedEvent.builder()
                .incidentId(incidentId)
                .oldStatus(IncidentStatus.OPEN)
                .newStatus(IncidentStatus.IN_PROGRESS)
                .changedBy(UUID.randomUUID())
                .timestamp(LocalDateTime.now())
                .build();

        listener.handleStatusChanged(objectMapper.writeValueAsString(event));

        verify(reportRepository, never()).save(any());
    }

    // ─── handleIncidentResolved ────────────────────────────────────────────────

    @Test
    @DisplayName("handleIncidentResolved: sets resolvedAt, resolutionTime and adds action")
    void handleIncidentResolved_UpdatesReport() throws Exception {
        UUID incidentId = UUID.randomUUID();
        LocalDateTime resolvedAt = LocalDateTime.now();

        IncidentReport existing = IncidentReport.builder()
                .incidentId(incidentId)
                .status("IN_PROGRESS")
                .build();

        when(reportRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(existing));

        IncidentResolvedEvent event = IncidentResolvedEvent.builder()
                .incidentId(incidentId)
                .resolvedBy(UUID.randomUUID())
                .resolvedAt(resolvedAt)
                .resolutionTimeMinutes(120L)
                .timestamp(LocalDateTime.now())
                .build();

        listener.handleIncidentResolved(objectMapper.writeValueAsString(event));

        verify(reportRepository).save(existing);
        assertThat(existing.getResolvedAt()).isEqualTo(resolvedAt);
        assertThat(existing.getResolutionTimeMinutes()).isEqualTo(120L);
        assertThat(existing.getActions().get(0).getAction()).isEqualTo("RESOLVED");
    }

    @Test
    @DisplayName("handleIncidentResolved: incident not found — no save")
    void handleIncidentResolved_NotFound_NoSave() throws Exception {
        UUID incidentId = UUID.randomUUID();
        when(reportRepository.findByIncidentId(incidentId)).thenReturn(Optional.empty());

        IncidentResolvedEvent event = IncidentResolvedEvent.builder()
                .incidentId(incidentId)
                .resolvedBy(UUID.randomUUID())
                .resolvedAt(LocalDateTime.now())
                .resolutionTimeMinutes(60L)
                .timestamp(LocalDateTime.now())
                .build();

        listener.handleIncidentResolved(objectMapper.writeValueAsString(event));

        verify(reportRepository, never()).save(any());
    }

    // ─── handleIncidentClosed ─────────────────────────────────────────────────

    @Test
    @DisplayName("handleIncidentClosed: sets closedAt and adds CLOSED action")
    void handleIncidentClosed_UpdatesReport() throws Exception {
        UUID incidentId = UUID.randomUUID();
        LocalDateTime closedAt = LocalDateTime.now();

        IncidentReport existing = IncidentReport.builder()
                .incidentId(incidentId)
                .status("RESOLVED")
                .build();

        when(reportRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(existing));

        IncidentClosedEvent event = IncidentClosedEvent.builder()
                .incidentId(incidentId)
                .closedBy(UUID.randomUUID())
                .closedAt(closedAt)
                .timestamp(LocalDateTime.now())
                .build();

        listener.handleIncidentClosed(objectMapper.writeValueAsString(event));

        verify(reportRepository).save(existing);
        assertThat(existing.getClosedAt()).isEqualTo(closedAt);
        assertThat(existing.getActions().get(0).getAction()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("handleIncidentClosed: incident not found — no save")
    void handleIncidentClosed_NotFound_NoSave() throws Exception {
        UUID incidentId = UUID.randomUUID();
        when(reportRepository.findByIncidentId(incidentId)).thenReturn(Optional.empty());

        IncidentClosedEvent event = IncidentClosedEvent.builder()
                .incidentId(incidentId)
                .closedBy(UUID.randomUUID())
                .closedAt(LocalDateTime.now())
                .timestamp(LocalDateTime.now())
                .build();

        listener.handleIncidentClosed(objectMapper.writeValueAsString(event));

        verify(reportRepository, never()).save(any());
    }
}
