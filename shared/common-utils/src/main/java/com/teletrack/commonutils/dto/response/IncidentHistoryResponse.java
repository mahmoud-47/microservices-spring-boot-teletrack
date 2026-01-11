package com.teletrack.commonutils.dto.response;

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
public class IncidentHistoryResponse {

    private UUID id;
    private UUID incidentId;
    private UUID userId;
    private String action;
    private String oldValue;
    private String newValue;
    private LocalDateTime timestamp;
}