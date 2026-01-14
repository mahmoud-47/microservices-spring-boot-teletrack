package com.teletrack.reportingservice.unit.service;

import com.teletrack.reportingservice.document.IncidentReport;
import com.teletrack.reportingservice.dto.response.IncidentSummaryResponse;
import com.teletrack.reportingservice.dto.response.TrendReportResponse;
import com.teletrack.reportingservice.dto.response.UserPerformanceResponse;
import com.teletrack.reportingservice.repository.IncidentReportRepository;
import com.teletrack.reportingservice.service.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private IncidentReportRepository reportRepository;

    @InjectMocks
    private ReportService reportService;

    @Test
    @DisplayName("Should calculate incident summary accurately")
    void getIncidentSummary_Success() {
        // Given
        List<IncidentReport> reports = Arrays.asList(
                IncidentReport.builder().status("OPEN").priority("HIGH").resolutionTimeMinutes(null).build(),
                IncidentReport.builder().status("RESOLVED").priority("MEDIUM").resolutionTimeMinutes(40L).build(),
                IncidentReport.builder().status("RESOLVED").priority("HIGH").resolutionTimeMinutes(20L).build()
        );
        when(reportRepository.findAll()).thenReturn(reports);

        // When
        IncidentSummaryResponse response = reportService.getIncidentSummary();

        // Then
        assertThat(response.getTotalIncidents()).isEqualTo(3);
        assertThat(response.getByStatus().get("RESOLVED")).isEqualTo(2);
        assertThat(response.getByPriority().get("HIGH")).isEqualTo(2);
        assertThat(response.getAverageResolutionTimeMinutes()).isEqualTo(30.0); // (40 + 20) / 2
    }

    @Test
    @DisplayName("Should handle empty summary gracefully")
    void getIncidentSummary_Empty() {
        // Given
        when(reportRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        IncidentSummaryResponse response = reportService.getIncidentSummary();

        // Then
        assertThat(response.getTotalIncidents()).isZero();
        assertThat(response.getAverageResolutionTimeMinutes()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should generate trend report with daily counts")
    void getTrendReport_Success() {
        // Given
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<IncidentReport> reports = List.of(
                IncidentReport.builder().createdAt(now).build(),
                IncidentReport.builder().createdAt(now).build()
        );
        when(reportRepository.findIncidentsInDateRange(any(), any())).thenReturn(reports);

        // When
        TrendReportResponse response = reportService.getTrendReport(today, today);

        // Then
        assertThat(response.getDailyCounts().get(today)).isEqualTo(2);
        assertThat(response.getPeriod()).contains(today.toString());
    }

    @Test
    @DisplayName("Should calculate user performance and rank by resolution count")
    void getUserPerformance_Success() {
        // Given
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        List<IncidentReport> reports = Arrays.asList(
                // User A: 2 assigned, 1 resolved
                IncidentReport.builder().assignedTo(userA).status("RESOLVED").resolutionTimeMinutes(10L).build(),
                IncidentReport.builder().assignedTo(userA).status("OPEN").build(),
                // User B: 1 assigned, 1 resolved (but better resolution time)
                IncidentReport.builder().assignedTo(userB).status("CLOSED").resolutionTimeMinutes(5L).build()
        );
        when(reportRepository.findAll()).thenReturn(reports);

        // When
        List<UserPerformanceResponse> results = reportService.getUserPerformance();

        // Then
        assertThat(results).hasSize(2);
        // Asserting sorting (both have 1 resolved, sorting is reversed by count)
        assertThat(results.get(0).getIncidentsAssigned()).isGreaterThanOrEqualTo(1);

        UserPerformanceResponse performanceA = results.stream()
                .filter(r -> r.getUserId().equals(userA)).findFirst().get();
        assertThat(performanceA.getIncidentsResolved()).isEqualTo(1);
        assertThat(performanceA.getAverageResolutionTimeMinutes()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("Should calculate single user performance by ID")
    void getUserPerformanceById_Success() {
        // Given
        UUID userId = UUID.randomUUID();
        List<IncidentReport> reports = List.of(
                IncidentReport.builder().assignedTo(userId).status("RESOLVED").resolutionTimeMinutes(100L).build()
        );
        when(reportRepository.findByAssignedTo(userId)).thenReturn(reports);

        // When
        UserPerformanceResponse response = reportService.getUserPerformanceById(userId);

        // Then
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getIncidentsResolved()).isEqualTo(1);
        assertThat(response.getAverageResolutionTimeMinutes()).isEqualTo(100.0);
    }
}