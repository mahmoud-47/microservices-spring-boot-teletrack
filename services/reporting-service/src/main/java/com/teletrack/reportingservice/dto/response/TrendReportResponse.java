package com.teletrack.reportingservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendReportResponse {

    private String period;
    private LocalDate startDate;
    private LocalDate endDate;
    private Map<LocalDate, Long> dailyCounts;
}