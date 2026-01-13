package com.teletrack.commonutils.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentResolvedEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String eventId;
    private String eventType;
    private LocalDateTime timestamp;
    private String correlationId;

    private UUID incidentId;
    private String incidentTitle;
    private UUID resolvedBy;
    private LocalDateTime resolvedAt;
    private Long resolutionTimeMinutes;
    private String reporterEmail;
}