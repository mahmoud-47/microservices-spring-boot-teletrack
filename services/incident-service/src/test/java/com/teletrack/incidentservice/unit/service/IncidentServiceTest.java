package com.teletrack.incidentservice.unit.service;

import com.teletrack.commonutils.dto.request.AssignIncidentRequest;
import com.teletrack.commonutils.dto.request.CreateIncidentRequest;
import com.teletrack.commonutils.dto.request.UpdateIncidentRequest;
import com.teletrack.commonutils.dto.response.IncidentResponse;
import com.teletrack.commonutils.enums.IncidentPriority;
import com.teletrack.commonutils.enums.IncidentStatus;
import com.teletrack.commonutils.event.IncidentAssignedEvent;
import com.teletrack.commonutils.event.IncidentClosedEvent;
import com.teletrack.commonutils.event.IncidentCreatedEvent;
import com.teletrack.commonutils.event.IncidentResolvedEvent;
import com.teletrack.commonutils.event.IncidentStatusChangedEvent;
import com.teletrack.commonutils.exception.BadRequestException;
import com.teletrack.commonutils.exception.ResourceNotFoundException;
import com.teletrack.incidentservice.client.UserServiceCaller;
import com.teletrack.incidentservice.entity.Incident;
import com.teletrack.incidentservice.entity.IncidentHistory;
import com.teletrack.incidentservice.mapper.IncidentMapper;
import com.teletrack.incidentservice.repository.IncidentHistoryRepository;
import com.teletrack.incidentservice.repository.IncidentRepository;
import com.teletrack.incidentservice.service.EventPublisher;
import com.teletrack.incidentservice.service.IncidentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("IncidentService Unit Tests")
class IncidentServiceTest {

    @Mock private IncidentRepository incidentRepository;
    @Mock private IncidentHistoryRepository incidentHistoryRepository;
    @Mock private IncidentMapper incidentMapper;
    @Mock private EventPublisher eventPublisher;

    @InjectMocks private IncidentService incidentService;

    private UUID userId;
    private Incident incident;
    private IncidentResponse dummyResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        incident = Incident.builder()
                .id(UUID.randomUUID())
                .title("Database Down")
                .description("Production DB is unresponsive")
                .priority(IncidentPriority.CRITICAL)
                .status(IncidentStatus.OPEN)
                .reportedBy(userId)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();

        dummyResponse = new IncidentResponse();
        dummyResponse.setId(incident.getId());

        lenient().when(incidentMapper.toIncidentResponse(any())).thenReturn(dummyResponse);
        lenient().when(incidentMapper.toIncidentHistoryResponseList(any())).thenReturn(new ArrayList<>());
        lenient().when(incidentHistoryRepository.findByIncidentIdOrderByTimestampDesc(any()))
                .thenReturn(new ArrayList<>());
    }

    // ─── createIncident ──────────────────────────────────────────────────────

    @Test
    @DisplayName("createIncident — saves incident and publishes event")
    void createIncident_Success() {
        CreateIncidentRequest request = new CreateIncidentRequest("Title", "Description here", IncidentPriority.HIGH);
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(incidentHistoryRepository.save(any(IncidentHistory.class))).thenReturn(new IncidentHistory());

        IncidentResponse result = incidentService.createIncident(request, userId);

        assertThat(result).isNotNull();
        verify(incidentRepository).save(any(Incident.class));
        verify(eventPublisher).publishEvent(eq("incident.created"), any(IncidentCreatedEvent.class));
    }

    @Test
    @DisplayName("createIncident — creates OPEN status regardless of request")
    void createIncident_AlwaysCreatesOpenStatus() {
        CreateIncidentRequest request = new CreateIncidentRequest("Title test", "A description that is long enough", IncidentPriority.LOW);
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> {
            Incident saved = inv.getArgument(0);
            assertThat(saved.getStatus()).isEqualTo(IncidentStatus.OPEN);
            assertThat(saved.getReportedBy()).isEqualTo(userId);
            return incident;
        });
        when(incidentHistoryRepository.save(any())).thenReturn(new IncidentHistory());

        incidentService.createIncident(request, userId);

        verify(incidentRepository).save(any(Incident.class));
    }

    // ─── getIncidentById ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getIncidentById — found returns response")
    void getIncidentById_Found() {
        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));

        IncidentResponse result = incidentService.getIncidentById(incident.getId());

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(incident.getId());
    }

    @Test
    @DisplayName("getIncidentById — not found throws ResourceNotFoundException")
    void getIncidentById_NotFound() {
        UUID missingId = UUID.randomUUID();
        when(incidentRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.getIncidentById(missingId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(missingId.toString());
    }

    // ─── getAllIncidents ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllIncidents — returns page of responses")
    void getAllIncidents_ReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Incident> page = new PageImpl<>(List.of(incident));
        when(incidentRepository.findAll(pageable)).thenReturn(page);

        Page<IncidentResponse> result = incidentService.getAllIncidents(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(incidentRepository).findAll(pageable);
    }

    @Test
    @DisplayName("getAllIncidents — empty DB returns empty page")
    void getAllIncidents_Empty() {
        Pageable pageable = PageRequest.of(0, 10);
        when(incidentRepository.findAll(pageable)).thenReturn(Page.empty());

        Page<IncidentResponse> result = incidentService.getAllIncidents(pageable);

        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    // ─── updateIncident ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateIncident — updates title and saves")
    void updateIncident_TitleChanged() {
        UpdateIncidentRequest request = new UpdateIncidentRequest("New Title", null, null, null);
        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any())).thenReturn(incident);
        when(incidentHistoryRepository.save(any())).thenReturn(new IncidentHistory());

        incidentService.updateIncident(incident.getId(), request, userId);

        assertThat(incident.getTitle()).isEqualTo("New Title");
        verify(incidentRepository).save(incident);
    }

    @Test
    @DisplayName("updateIncident — no changes skips save")
    void updateIncident_NoChanges_SkipsSave() {
        // All fields null → nothing changes
        UpdateIncidentRequest request = new UpdateIncidentRequest(null, null, null, null);
        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));

        incidentService.updateIncident(incident.getId(), request, userId);

        verify(incidentRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateIncident — not found throws exception")
    void updateIncident_NotFound() {
        UUID missingId = UUID.randomUUID();
        when(incidentRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.updateIncident(
                missingId, new UpdateIncidentRequest("T", null, null, null), userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── assignIncident ───────────────────────────────────────────────────────

    @Test
    @DisplayName("assignIncident — valid user assigns and publishes event")
    void assignIncident_ValidUser() {
        UUID assigneeId = UUID.randomUUID();
        AssignIncidentRequest request = new AssignIncidentRequest(assigneeId);

        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any())).thenReturn(incident);
        when(incidentHistoryRepository.save(any())).thenReturn(new IncidentHistory());

        try (MockedStatic<UserServiceCaller> mock = mockStatic(UserServiceCaller.class)) {
            mock.when(() -> UserServiceCaller.validateUser(assigneeId)).thenReturn(true);
            mock.when(() -> UserServiceCaller.getUserEmail(any())).thenReturn(Optional.empty());

            incidentService.assignIncident(incident.getId(), request, userId);
        }

        assertThat(incident.getAssignedTo()).isEqualTo(assigneeId);
        verify(eventPublisher).publishEvent(eq("incident.assigned"), any(IncidentAssignedEvent.class));
    }

    @Test
    @DisplayName("assignIncident — invalid user throws BadRequestException")
    void assignIncident_InvalidUser_Throws() {
        UUID assigneeId = UUID.randomUUID();
        AssignIncidentRequest request = new AssignIncidentRequest(assigneeId);

        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));

        try (MockedStatic<UserServiceCaller> mock = mockStatic(UserServiceCaller.class)) {
            mock.when(() -> UserServiceCaller.validateUser(assigneeId)).thenReturn(false);

            assertThatThrownBy(() -> incidentService.assignIncident(incident.getId(), request, userId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("User not found");
        }
    }

    @Test
    @DisplayName("assignIncident — incident not found throws exception")
    void assignIncident_IncidentNotFound() {
        UUID missingId = UUID.randomUUID();
        when(incidentRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.assignIncident(
                missingId, new AssignIncidentRequest(UUID.randomUUID()), userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── changeStatus ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("changeStatus — RESOLVED sets resolvedAt and publishes resolved event")
    void changeStatus_ToResolved_CalculatesTime() {
        UUID resolverId = UUID.randomUUID();
        when(incidentRepository.findById(any())).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(incidentHistoryRepository.save(any())).thenReturn(new IncidentHistory());

        try (MockedStatic<UserServiceCaller> mock = mockStatic(UserServiceCaller.class)) {
            mock.when(() -> UserServiceCaller.getUserEmail(any())).thenReturn(Optional.empty());

            incidentService.changeStatus(incident.getId(), IncidentStatus.RESOLVED, resolverId);
        }

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).isNotNull();
        verify(eventPublisher).publishEvent(eq("incident.resolved"), any(IncidentResolvedEvent.class));
        verify(eventPublisher).publishEvent(eq("incident.status.changed"), any(IncidentStatusChangedEvent.class));
    }

    @Test
    @DisplayName("changeStatus — CLOSED sets closedAt and publishes closed event")
    void changeStatus_ToClosed() {
        UUID closerId = UUID.randomUUID();
        when(incidentRepository.findById(any())).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any())).thenReturn(incident);
        when(incidentHistoryRepository.save(any())).thenReturn(new IncidentHistory());

        try (MockedStatic<UserServiceCaller> mock = mockStatic(UserServiceCaller.class)) {
            mock.when(() -> UserServiceCaller.getUserEmail(any())).thenReturn(Optional.empty());

            incidentService.changeStatus(incident.getId(), IncidentStatus.CLOSED, closerId);
        }

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(incident.getClosedAt()).isNotNull();
        verify(eventPublisher).publishEvent(eq("incident.closed"), any(IncidentClosedEvent.class));
    }

    @Test
    @DisplayName("changeStatus — same status is a no-op")
    void changeStatus_SameStatus_NoOp() {
        when(incidentRepository.findById(any())).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any())).thenReturn(incident);

        try (MockedStatic<UserServiceCaller> mock = mockStatic(UserServiceCaller.class)) {
            mock.when(() -> UserServiceCaller.getUserEmail(any())).thenReturn(Optional.empty());

            incidentService.changeStatus(incident.getId(), IncidentStatus.OPEN, userId);
        }

        verify(eventPublisher, never()).publishEvent(any(), any());
    }

    @Test
    @DisplayName("changeStatus — not found throws exception")
    void changeStatus_NotFound() {
        UUID missingId = UUID.randomUUID();
        when(incidentRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.changeStatus(missingId, IncidentStatus.RESOLVED, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── query methods ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getIncidentsByStatus — returns matching incidents")
    void getIncidentsByStatus_ReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(incidentRepository.findByStatus(IncidentStatus.OPEN, pageable))
                .thenReturn(new PageImpl<>(List.of(incident)));

        Page<IncidentResponse> result = incidentService.getIncidentsByStatus(IncidentStatus.OPEN, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getIncidentsByReportedBy — returns user's incidents")
    void getIncidentsByReportedBy_ReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(incidentRepository.findByReportedBy(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(incident)));

        Page<IncidentResponse> result = incidentService.getIncidentsByReportedBy(userId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getIncidentsByAssignedTo — returns assigned incidents")
    void getIncidentsByAssignedTo_ReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(incidentRepository.findByAssignedTo(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(incident)));

        Page<IncidentResponse> result = incidentService.getIncidentsByAssignedTo(userId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }
}
