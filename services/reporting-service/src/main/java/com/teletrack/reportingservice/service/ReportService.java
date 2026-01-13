package com.teletrack.reportingservice.service;

import com.teletrack.reportingservice.document.IncidentReport;
import com.teletrack.reportingservice.dto.response.IncidentSummaryResponse;
import com.teletrack.reportingservice.dto.response.TrendReportResponse;
import com.teletrack.reportingservice.dto.response.UserPerformanceResponse;
import com.teletrack.reportingservice.repository.IncidentReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final IncidentReportRepository reportRepository;

    @Cacheable(value = "incident-summary", key = "'all'")
    public IncidentSummaryResponse getIncidentSummary() {
        log.info("Generating incident summary report");

        List<IncidentReport> allIncidents = reportRepository.findAll();

        Map<String, Long> byStatus = allIncidents.stream()
                .collect(Collectors.groupingBy(IncidentReport::getStatus, Collectors.counting()));

        Map<String, Long> byPriority = allIncidents.stream()
                .collect(Collectors.groupingBy(IncidentReport::getPriority, Collectors.counting()));

        Double avgResolutionTime = allIncidents.stream()
                .filter(i -> i.getResolutionTimeMinutes() != null)
                .mapToLong(IncidentReport::getResolutionTimeMinutes)
                .average()
                .orElse(0.0);

        return IncidentSummaryResponse.builder()
                .totalIncidents((long) allIncidents.size())
                .byStatus(byStatus)
                .byPriority(byPriority)
                .averageResolutionTimeMinutes(avgResolutionTime)
                .build();
    }

    @Cacheable(value = "trend-report", key = "#startDate + '-' + #endDate")
    public TrendReportResponse getTrendReport(LocalDate startDate, LocalDate endDate) {
        log.info("Generating trend report from {} to {}", startDate, endDate);

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        List<IncidentReport> incidents = reportRepository.findIncidentsInDateRange(start, end);

        Map<LocalDate, Long> dailyCounts = incidents.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getCreatedAt().toLocalDate(),
                        Collectors.counting()
                ));

        return TrendReportResponse.builder()
                .period(startDate + " to " + endDate)
                .startDate(startDate)
                .endDate(endDate)
                .dailyCounts(dailyCounts)
                .build();
    }

    @Cacheable(value = "user-performance", key = "'all'")
    public List<UserPerformanceResponse> getUserPerformance() {
        log.info("Generating user performance report");

        List<IncidentReport> allIncidents = reportRepository.findAll();

        Map<UUID, List<IncidentReport>> byAssignee = allIncidents.stream()
                .filter(i -> i.getAssignedTo() != null)
                .collect(Collectors.groupingBy(IncidentReport::getAssignedTo));

        System.out.println("+++++ list = " + allIncidents);
        System.out.println("***** byAssignee = " + byAssignee);

        return byAssignee.entrySet().stream()
                .map(entry -> {
                    UUID userId = entry.getKey();
                    List<IncidentReport> userIncidents = entry.getValue();

                    long assigned = userIncidents.size();
                    long resolved = userIncidents.stream()
                            .filter(i -> "RESOLVED".equals(i.getStatus()) || "CLOSED".equals(i.getStatus()))
                            .count();

                    double avgResolution = userIncidents.stream()
                            .filter(i -> i.getResolutionTimeMinutes() != null)
                            .mapToLong(IncidentReport::getResolutionTimeMinutes)
                            .average()
                            .orElse(0.0);

                    return UserPerformanceResponse.builder()
                            .userId(userId)
                            .incidentsAssigned(assigned)
                            .incidentsResolved(resolved)
                            .averageResolutionTimeMinutes(avgResolution)
                            .build();
                })
                .sorted(Comparator.comparingLong(UserPerformanceResponse::getIncidentsResolved).reversed())
                .collect(Collectors.toList());
    }

    @Cacheable(value = "user-performance", key = "#userId")
    public UserPerformanceResponse getUserPerformanceById(UUID userId) {
        log.info("Generating performance report for user: {}", userId);

        List<IncidentReport> userIncidents = reportRepository.findByAssignedTo(userId);

        long assigned = userIncidents.size();
        long resolved = userIncidents.stream()
                .filter(i -> "RESOLVED".equals(i.getStatus()) || "CLOSED".equals(i.getStatus()))
                .count();

        double avgResolution = userIncidents.stream()
                .filter(i -> i.getResolutionTimeMinutes() != null)
                .mapToLong(IncidentReport::getResolutionTimeMinutes)
                .average()
                .orElse(0.0);

        return UserPerformanceResponse.builder()
                .userId(userId)
                .incidentsAssigned(assigned)
                .incidentsResolved(resolved)
                .averageResolutionTimeMinutes(avgResolution)
                .build();
    }
}