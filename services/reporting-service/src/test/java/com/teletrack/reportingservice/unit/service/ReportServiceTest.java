package com.teletrack.reportingservice.unit.service;

import com.teletrack.reportingservice.document.IncidentReport;
import com.teletrack.reportingservice.dto.response.IncidentSummaryResponse;
import com.teletrack.reportingservice.dto.response.TrendReportResponse;
import com.teletrack.reportingservice.dto.response.UserPerformanceResponse;
import com.teletrack.reportingservice.repository.IncidentReportRepository;
import com.teletrack.reportingservice.service.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService Unit Tests")
class ReportServiceTest {

    @Mock
    private IncidentReportRepository reportRepository;

    @InjectMocks
    private ReportService reportService;

    // ─── getIncidentSummary ────────────────────────────────────────────────────

    @Test
    @DisplayName("getIncidentSummary: calculates totals, status/priority counts and avg resolution")
    void getIncidentSummary_Success() {
        List<IncidentReport> reports = Arrays.asList(
                IncidentReport.builder().status("OPEN").priority("HIGH").resolutionTimeMinutes(null).build(),
                IncidentReport.builder().status("RESOLVED").priority("MEDIUM").resolutionTimeMinutes(40L).build(),
                IncidentReport.builder().status("RESOLVED").priority("HIGH").resolutionTimeMinutes(20L).build()
        );
        when(reportRepository.findAll()).thenReturn(reports);

        IncidentSummaryResponse response = reportService.getIncidentSummary();

        assertThat(response.getTotalIncidents()).isEqualTo(3);
        assertThat(response.getByStatus().get("RESOLVED")).isEqualTo(2);
        assertThat(response.getByPriority().get("HIGH")).isEqualTo(2);
        assertThat(response.getAverageResolutionTimeMinutes()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("getIncidentSummary: empty repository returns zeros")
    void getIncidentSummary_Empty() {
        when(reportRepository.findAll()).thenReturn(Collections.emptyList());

        IncidentSummaryResponse response = reportService.getIncidentSummary();

        assertThat(response.getTotalIncidents()).isZero();
        assertThat(response.getAverageResolutionTimeMinutes()).isEqualTo(0.0);
        assertThat(response.getByStatus()).isEmpty();
        assertThat(response.getByPriority()).isEmpty();
    }

    @Test
    @DisplayName("getIncidentSummary: incidents with no resolution time are excluded from avg")
    void getIncidentSummary_NoResolutionTime_AvgIsZero() {
        List<IncidentReport> reports = List.of(
                IncidentReport.builder().status("OPEN").priority("LOW").resolutionTimeMinutes(null).build(),
                IncidentReport.builder().status("IN_PROGRESS").priority("MEDIUM").resolutionTimeMinutes(null).build()
        );
        when(reportRepository.findAll()).thenReturn(reports);

        IncidentSummaryResponse response = reportService.getIncidentSummary();

        assertThat(response.getTotalIncidents()).isEqualTo(2);
        assertThat(response.getAverageResolutionTimeMinutes()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getIncidentSummary: single incident with resolution time")
    void getIncidentSummary_SingleResolved() {
        when(reportRepository.findAll()).thenReturn(List.of(
                IncidentReport.builder().status("RESOLVED").priority("CRITICAL").resolutionTimeMinutes(60L).build()
        ));

        IncidentSummaryResponse response = reportService.getIncidentSummary();

        assertThat(response.getTotalIncidents()).isEqualTo(1);
        assertThat(response.getAverageResolutionTimeMinutes()).isEqualTo(60.0);
    }

    // ─── getTrendReport ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getTrendReport: groups daily counts correctly")
    void getTrendReport_Success() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<IncidentReport> reports = List.of(
                IncidentReport.builder().createdAt(now).build(),
                IncidentReport.builder().createdAt(now).build()
        );
        when(reportRepository.findIncidentsInDateRange(any(), any())).thenReturn(reports);

        TrendReportResponse response = reportService.getTrendReport(today, today);

        assertThat(response.getDailyCounts().get(today)).isEqualTo(2);
        assertThat(response.getPeriod()).contains(today.toString());
    }

    @Test
    @DisplayName("getTrendReport: empty date range returns empty counts")
    void getTrendReport_Empty() {
        LocalDate start = LocalDate.now().minusDays(7);
        LocalDate end = LocalDate.now();
        when(reportRepository.findIncidentsInDateRange(any(), any())).thenReturn(Collections.emptyList());

        TrendReportResponse response = reportService.getTrendReport(start, end);

        assertThat(response.getDailyCounts()).isEmpty();
        assertThat(response.getStartDate()).isEqualTo(start);
        assertThat(response.getEndDate()).isEqualTo(end);
    }

    @Test
    @DisplayName("getTrendReport: incidents on different days are grouped separately")
    void getTrendReport_MultipleDays() {
        LocalDate day1 = LocalDate.now().minusDays(2);
        LocalDate day2 = LocalDate.now().minusDays(1);
        List<IncidentReport> reports = List.of(
                IncidentReport.builder().createdAt(day1.atTime(10, 0)).build(),
                IncidentReport.builder().createdAt(day1.atTime(15, 0)).build(),
                IncidentReport.builder().createdAt(day2.atTime(9, 0)).build()
        );
        when(reportRepository.findIncidentsInDateRange(any(), any())).thenReturn(reports);

        TrendReportResponse response = reportService.getTrendReport(day1, day2);

        assertThat(response.getDailyCounts()).hasSize(2);
        assertThat(response.getDailyCounts().get(day1)).isEqualTo(2);
        assertThat(response.getDailyCounts().get(day2)).isEqualTo(1);
    }

    // ─── getUserPerformance ────────────────────────────────────────────────────

    @Test
    @DisplayName("getUserPerformance: calculates per-user metrics and sorts by resolved desc")
    void getUserPerformance_Success() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        List<IncidentReport> reports = Arrays.asList(
                IncidentReport.builder().assignedTo(userA).status("RESOLVED").resolutionTimeMinutes(10L).build(),
                IncidentReport.builder().assignedTo(userA).status("OPEN").build(),
                IncidentReport.builder().assignedTo(userB).status("CLOSED").resolutionTimeMinutes(5L).build()
        );
        when(reportRepository.findAll()).thenReturn(reports);

        List<UserPerformanceResponse> results = reportService.getUserPerformance();

        assertThat(results).hasSize(2);
        UserPerformanceResponse perfA = results.stream().filter(r -> r.getUserId().equals(userA)).findFirst().orElseThrow();
        assertThat(perfA.getIncidentsResolved()).isEqualTo(1);
        assertThat(perfA.getAverageResolutionTimeMinutes()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("getUserPerformance: incidents without assignee are excluded")
    void getUserPerformance_UnassignedExcluded() {
        when(reportRepository.findAll()).thenReturn(List.of(
                IncidentReport.builder().assignedTo(null).status("OPEN").build(),
                IncidentReport.builder().assignedTo(null).status("RESOLVED").resolutionTimeMinutes(30L).build()
        ));

        List<UserPerformanceResponse> results = reportService.getUserPerformance();

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("getUserPerformance: empty repository returns empty list")
    void getUserPerformance_EmptyRepo() {
        when(reportRepository.findAll()).thenReturn(Collections.emptyList());

        assertThat(reportService.getUserPerformance()).isEmpty();
    }

    @Test
    @DisplayName("getUserPerformance: CLOSED incidents count as resolved")
    void getUserPerformance_ClosedCountsAsResolved() {
        UUID userId = UUID.randomUUID();
        when(reportRepository.findAll()).thenReturn(List.of(
                IncidentReport.builder().assignedTo(userId).status("CLOSED").resolutionTimeMinutes(20L).build()
        ));

        List<UserPerformanceResponse> results = reportService.getUserPerformance();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getIncidentsResolved()).isEqualTo(1);
    }

    // ─── getUserPerformanceById ────────────────────────────────────────────────

    @Test
    @DisplayName("getUserPerformanceById: returns performance metrics for a specific user")
    void getUserPerformanceById_Success() {
        UUID userId = UUID.randomUUID();
        List<IncidentReport> reports = List.of(
                IncidentReport.builder().assignedTo(userId).status("RESOLVED").resolutionTimeMinutes(100L).build()
        );
        when(reportRepository.findByAssignedTo(userId)).thenReturn(reports);

        UserPerformanceResponse response = reportService.getUserPerformanceById(userId);

        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getIncidentsResolved()).isEqualTo(1);
        assertThat(response.getAverageResolutionTimeMinutes()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("getUserPerformanceById: user with no incidents returns zeros")
    void getUserPerformanceById_NoIncidents() {
        UUID userId = UUID.randomUUID();
        when(reportRepository.findByAssignedTo(userId)).thenReturn(Collections.emptyList());

        UserPerformanceResponse response = reportService.getUserPerformanceById(userId);

        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getIncidentsAssigned()).isZero();
        assertThat(response.getIncidentsResolved()).isZero();
        assertThat(response.getAverageResolutionTimeMinutes()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getUserPerformanceById: CLOSED incidents count as resolved")
    void getUserPerformanceById_ClosedCountsAsResolved() {
        UUID userId = UUID.randomUUID();
        when(reportRepository.findByAssignedTo(userId)).thenReturn(List.of(
                IncidentReport.builder().assignedTo(userId).status("CLOSED").resolutionTimeMinutes(45L).build(),
                IncidentReport.builder().assignedTo(userId).status("OPEN").build()
        ));

        UserPerformanceResponse response = reportService.getUserPerformanceById(userId);

        assertThat(response.getIncidentsAssigned()).isEqualTo(2);
        assertThat(response.getIncidentsResolved()).isEqualTo(1);
    }

    @Test
    @DisplayName("getUserPerformanceById: avg resolution excludes incidents with no resolution time")
    void getUserPerformanceById_AvgExcludesNullResolutionTime() {
        UUID userId = UUID.randomUUID();
        when(reportRepository.findByAssignedTo(userId)).thenReturn(List.of(
                IncidentReport.builder().assignedTo(userId).status("RESOLVED").resolutionTimeMinutes(60L).build(),
                IncidentReport.builder().assignedTo(userId).status("OPEN").resolutionTimeMinutes(null).build()
        ));

        UserPerformanceResponse response = reportService.getUserPerformanceById(userId);

        assertThat(response.getAverageResolutionTimeMinutes()).isEqualTo(60.0);
    }
}
