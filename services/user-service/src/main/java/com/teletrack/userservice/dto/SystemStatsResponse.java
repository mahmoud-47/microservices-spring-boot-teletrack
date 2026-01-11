package com.teletrack.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatsResponse {
    private Long totalUsers;
    private Long activeUsers;
    private Long pendingApproval;
    private Map<String, Long> usersByRole;
    private LocalDateTime lastUpdated;
}