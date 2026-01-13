package com.teletrack.reportingservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentSummaryResponse {

    private Long totalIncidents;
    private Map<String, Long> byStatus;
    private Map<String, Long> byPriority;
    private Double averageResolutionTimeMinutes;
}
