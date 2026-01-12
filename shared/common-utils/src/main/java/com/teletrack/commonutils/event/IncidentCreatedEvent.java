package com.teletrack.commonutils.event;

import com.teletrack.commonutils.enums.IncidentPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentCreatedEvent {

    private String eventId;
    private String eventType;
    private LocalDateTime timestamp;
    private String correlationId;

    // Incident details
    private UUID incidentId;
    private String title;
    private String description;
    private IncidentPriority priority;
    private UUID reportedBy;
}