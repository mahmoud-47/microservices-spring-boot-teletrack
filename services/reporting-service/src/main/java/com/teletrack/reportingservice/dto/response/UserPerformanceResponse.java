package com.teletrack.reportingservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPerformanceResponse {

    private UUID userId;
    private String userFirstName;
    private String userLastName;
    private Long incidentsAssigned;
    private Long incidentsResolved;
    private Double averageResolutionTimeMinutes;
}