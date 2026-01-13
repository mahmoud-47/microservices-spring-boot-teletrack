package com.teletrack.reportingservice.controller;

import com.teletrack.reportingservice.client.AIServiceClient;
import com.teletrack.reportingservice.dto.response.IncidentSummaryResponse;
import com.teletrack.reportingservice.dto.response.TrendReportResponse;
import com.teletrack.reportingservice.dto.response.UserPerformanceResponse;
import com.teletrack.reportingservice.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Reporting", description = "APIs for incident reports and analytics")
public class ReportController {

    private final ReportService reportService;
    private final AIServiceClient aiServiceClient;

    @GetMapping("/summary")
    @Operation(
            summary = "Get incident summary",
            description = "Retrieve overall incident statistics",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<IncidentSummaryResponse> getIncidentSummary() {
        IncidentSummaryResponse summary = reportService.getIncidentSummary();
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/trends")
    @Operation(
            summary = "Get trend report",
            description = "Retrieve incident trends over a date range",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<TrendReportResponse> getTrendReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        TrendReportResponse trends = reportService.getTrendReport(startDate, endDate);
        return ResponseEntity.ok(trends);
    }

    @GetMapping("/user-performance")
    @Operation(
            summary = "Get user performance",
            description = "Retrieve performance metrics for all users",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<List<UserPerformanceResponse>> getUserPerformance() {
        List<UserPerformanceResponse> performance = reportService.getUserPerformance();
        return ResponseEntity.ok(performance);
    }

    @GetMapping("/user-performance/{userId}")
    @Operation(
            summary = "Get user performance by ID",
            description = "Retrieve performance metrics for a specific user",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<UserPerformanceResponse> getUserPerformanceById(@PathVariable UUID userId) {
        UserPerformanceResponse performance = reportService.getUserPerformanceById(userId);
        return ResponseEntity.ok(performance);
    }

    @PostMapping("/ai-insights")
    @Operation(
            summary = "Get AI-generated insights",
            description = "Generate automated insights using AI analysis",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getAIInsights() {
        IncidentSummaryResponse summary = reportService.getIncidentSummary();

        Map<String, Object> reportData = new HashMap<>();
        reportData.put("totalIncidents", summary.getTotalIncidents());
        reportData.put("byStatus", summary.getByStatus());
        reportData.put("byPriority", summary.getByPriority());
        reportData.put("averageResolutionTimeMinutes", summary.getAverageResolutionTimeMinutes());

        Map<String, Object> insights = aiServiceClient.getInsights(reportData);
        return ResponseEntity.ok(insights);
    }

    @PostMapping("/ai-summary")
    @Operation(
            summary = "Get AI-generated summary",
            description = "Generate textual summary of incident patterns",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getAISummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        TrendReportResponse trends = reportService.getTrendReport(startDate, endDate);
        IncidentSummaryResponse summary = reportService.getIncidentSummary();

        Map<String, Object> reportData = new HashMap<>();
        reportData.put("period", trends.getPeriod());
        reportData.put("dailyCounts", trends.getDailyCounts());
        reportData.put("totalIncidents", summary.getTotalIncidents());
        reportData.put("byStatus", summary.getByStatus());
        reportData.put("byPriority", summary.getByPriority());

        Map<String, Object> aiSummary = aiServiceClient.getSummary(reportData);
        return ResponseEntity.ok(aiSummary);
    }
}