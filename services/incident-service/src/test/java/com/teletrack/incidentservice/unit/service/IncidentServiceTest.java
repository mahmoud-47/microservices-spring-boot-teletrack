package com.teletrack.incidentservice.unit.service;

import com.teletrack.commonutils.dto.request.CreateIncidentRequest;
import com.teletrack.commonutils.dto.response.IncidentResponse;
import com.teletrack.commonutils.enums.IncidentPriority;
import com.teletrack.commonutils.enums.IncidentStatus;
import com.teletrack.commonutils.event.IncidentCreatedEvent;
import com.teletrack.commonutils.event.IncidentResolvedEvent;
import com.teletrack.incidentservice.entity.Incident;
import com.teletrack.incidentservice.entity.IncidentHistory;
import com.teletrack.incidentservice.mapper.IncidentMapper;
import com.teletrack.incidentservice.repository.IncidentHistoryRepository;
import com.teletrack.incidentservice.repository.IncidentRepository;
import com.teletrack.incidentservice.service.EventPublisher;
import com.teletrack.incidentservice.service.IncidentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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

        // Ensure the mapper doesn't return null
        lenient().when(incidentMapper.toIncidentResponse(any())).thenReturn(dummyResponse);
        lenient().when(incidentMapper.toIncidentHistoryResponseList(any())).thenReturn(new ArrayList<>());
    }

    @Test
    @DisplayName("Should save incident and publish event on creation")
    void createIncident_Success() {
        // Given
        CreateIncidentRequest request = new CreateIncidentRequest("Title", "Desc", IncidentPriority.HIGH);
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(incidentHistoryRepository.save(any(IncidentHistory.class))).thenReturn(new IncidentHistory());

        // When
        IncidentResponse result = incidentService.createIncident(request, userId);

        // Then
        assertThat(result).isNotNull();
        verify(incidentRepository).save(any(Incident.class));
        verify(eventPublisher).publishEvent(eq("incident.created"), any(IncidentCreatedEvent.class));
    }

    @Test
    @DisplayName("Should calculate resolution time when status changed to RESOLVED")
    void changeStatus_ToResolved_CalculatesTime() {
        // Given
        UUID resolverId = UUID.randomUUID();
        when(incidentRepository.findById(any())).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(incidentHistoryRepository.findByIncidentIdOrderByTimestampDesc(any())).thenReturn(new ArrayList<>());

        // When
        incidentService.changeStatus(incident.getId(), IncidentStatus.RESOLVED, resolverId);

        // Then
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).isNotNull();

        verify(eventPublisher).publishEvent(eq("incident.resolved"), any(IncidentResolvedEvent.class));
    }
}