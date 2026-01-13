package com.teletrack.reportingservice.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Document(collection = "incident_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentReport {

    @Id
    private String id;

    private UUID incidentId;
    private String title;
    private String status;
    private String priority;
    private UUID reportedBy;
    private UUID assignedTo;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private Long resolutionTimeMinutes;

    @Builder.Default
    private List<IncidentAction> actions = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IncidentAction {
        private String action;
        private UUID performedBy;
        private LocalDateTime timestamp;
        private String oldValue;
        private String newValue;
    }
}