package com.teletrack.commonutils.event;

import com.teletrack.commonutils.enums.IncidentStatus;
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
public class IncidentStatusChangedEvent {

    private String eventId;
    private String eventType;
    private LocalDateTime timestamp;
    private String correlationId;

    // Status change details
    private UUID incidentId;
    private String incidentTitle;
    private IncidentStatus oldStatus;
    private IncidentStatus newStatus;
    private UUID changedBy;
}