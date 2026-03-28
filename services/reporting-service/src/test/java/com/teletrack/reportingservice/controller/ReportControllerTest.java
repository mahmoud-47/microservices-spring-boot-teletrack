package com.teletrack.reportingservice.controller;

import com.teletrack.reportingservice.client.AIServiceClient;
import com.teletrack.reportingservice.config.JwtService;
import com.teletrack.reportingservice.config.TestSecurityConfig;
import com.teletrack.reportingservice.dto.response.IncidentSummaryResponse;
import com.teletrack.reportingservice.dto.response.TrendReportResponse;
import com.teletrack.reportingservice.dto.response.UserPerformanceResponse;
import com.teletrack.reportingservice.service.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("controller")
@WebMvcTest(controllers = {ReportController.class, HelloController.class})
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("ReportController Tests")
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private AIServiceClient aiServiceClient;

    @MockitoBean
    private JwtService jwtService;

    // ─── HelloController ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /reports/hello — public endpoint returns 200")
    void hello_ReturnsOk() throws Exception {
        mockMvc.perform(get("/reports/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello From Reporting Service !!"));
    }

    @Test
    @DisplayName("GET /reports/hello — unauthenticated returns 200 (public)")
    void hello_Unauthenticated_ReturnsOk() throws Exception {
        mockMvc.perform(get("/reports/hello"))
                .andExpect(status().isOk());
    }

    // ─── GET /reports/summary ─────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /reports/summary — ADMIN returns 200 with summary")
    void getSummary_Admin_Returns200() throws Exception {
        IncidentSummaryResponse summary = IncidentSummaryResponse.builder()
                .totalIncidents(10L)
                .byStatus(Map.of("OPEN", 5L, "RESOLVED", 5L))
                .byPriority(Map.of("HIGH", 3L))
                .averageResolutionTimeMinutes(45.0)
                .build();
        when(reportService.getIncidentSummary()).thenReturn(summary);

        mockMvc.perform(get("/reports/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncidents").value(10))
                .andExpect(jsonPath("$.averageResolutionTimeMinutes").value(45.0));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    @DisplayName("GET /reports/summary — OPERATOR returns 200")
    void getSummary_Operator_Returns200() throws Exception {
        when(reportService.getIncidentSummary()).thenReturn(
                IncidentSummaryResponse.builder().totalIncidents(0L).averageResolutionTimeMinutes(0.0).build()
        );

        mockMvc.perform(get("/reports/summary"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /reports/summary — unauthenticated returns 401")
    void getSummary_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/reports/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "SUPPORT")
    @DisplayName("GET /reports/summary — SUPPORT returns 403")
    void getSummary_Support_Returns403() throws Exception {
        mockMvc.perform(get("/reports/summary"))
                .andExpect(status().isForbidden());
    }

    // ─── GET /reports/trends ──────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /reports/trends — with date params returns 200")
    void getTrends_Admin_Returns200() throws Exception {
        TrendReportResponse trends = TrendReportResponse.builder()
                .period("2024-01-01 to 2024-01-31")
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 1, 31))
                .dailyCounts(Map.of())
                .build();
        when(reportService.getTrendReport(any(), any())).thenReturn(trends);

        mockMvc.perform(get("/reports/trends")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("2024-01-01 to 2024-01-31"));
    }

    @Test
    @DisplayName("GET /reports/trends — unauthenticated returns 401")
    void getTrends_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/reports/trends")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-31"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "SUPPORT")
    @DisplayName("GET /reports/trends — SUPPORT returns 403")
    void getTrends_Support_Returns403() throws Exception {
        mockMvc.perform(get("/reports/trends")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-31"))
                .andExpect(status().isForbidden());
    }

    // ─── GET /reports/user-performance ────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /reports/user-performance — ADMIN returns 200 with list")
    void getUserPerformance_Admin_Returns200() throws Exception {
        List<UserPerformanceResponse> performance = List.of(
                UserPerformanceResponse.builder()
                        .userId(UUID.randomUUID())
                        .incidentsAssigned(5L)
                        .incidentsResolved(3L)
                        .averageResolutionTimeMinutes(60.0)
                        .build()
        );
        when(reportService.getUserPerformance()).thenReturn(performance);

        mockMvc.perform(get("/reports/user-performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].incidentsAssigned").value(5))
                .andExpect(jsonPath("$[0].incidentsResolved").value(3));
    }

    @Test
    @DisplayName("GET /reports/user-performance — unauthenticated returns 401")
    void getUserPerformance_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/reports/user-performance"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /reports/user-performance/{userId} ───────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /reports/user-performance/{userId} — ADMIN returns 200")
    void getUserPerformanceById_Admin_Returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UserPerformanceResponse response = UserPerformanceResponse.builder()
                .userId(userId)
                .incidentsAssigned(3L)
                .incidentsResolved(2L)
                .averageResolutionTimeMinutes(30.0)
                .build();
        when(reportService.getUserPerformanceById(userId)).thenReturn(response);

        mockMvc.perform(get("/reports/user-performance/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.incidentsResolved").value(2));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    @DisplayName("GET /reports/user-performance/{userId} — OPERATOR returns 200")
    void getUserPerformanceById_Operator_Returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        when(reportService.getUserPerformanceById(userId)).thenReturn(
                UserPerformanceResponse.builder().userId(userId).incidentsAssigned(0L).incidentsResolved(0L).averageResolutionTimeMinutes(0.0).build()
        );

        mockMvc.perform(get("/reports/user-performance/{userId}", userId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /reports/user-performance/{userId} — unauthenticated returns 401")
    void getUserPerformanceById_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/reports/user-performance/{userId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ─── POST /reports/ai-insights ────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /reports/ai-insights — AI service succeeds returns 200")
    void getAIInsights_Success_Returns200() throws Exception {
        when(reportService.getIncidentSummary()).thenReturn(
                IncidentSummaryResponse.builder().totalIncidents(5L).averageResolutionTimeMinutes(20.0).build()
        );
        when(aiServiceClient.getInsights(any())).thenReturn(Map.of("insights", "All systems nominal"));

        mockMvc.perform(post("/reports/ai-insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insights").value("All systems nominal"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /reports/ai-insights — AI service throws → returns 500 with error")
    void getAIInsights_ServiceError_Returns500() throws Exception {
        when(reportService.getIncidentSummary()).thenThrow(new RuntimeException("DB unreachable"));

        mockMvc.perform(post("/reports/ai-insights"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /reports/ai-insights — unauthenticated returns 401")
    void getAIInsights_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/reports/ai-insights"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "SUPPORT")
    @DisplayName("POST /reports/ai-insights — SUPPORT returns 403")
    void getAIInsights_Support_Returns403() throws Exception {
        mockMvc.perform(post("/reports/ai-insights"))
                .andExpect(status().isForbidden());
    }

    // ─── POST /reports/ai-summary ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /reports/ai-summary — with date params returns 200")
    void getAISummary_Admin_Returns200() throws Exception {
        when(reportService.getTrendReport(any(), any())).thenReturn(
                TrendReportResponse.builder().period("2024-01-01 to 2024-01-07").dailyCounts(Map.of()).build()
        );
        when(reportService.getIncidentSummary()).thenReturn(
                IncidentSummaryResponse.builder().totalIncidents(10L).averageResolutionTimeMinutes(15.0).build()
        );
        when(aiServiceClient.getSummary(any())).thenReturn(Map.of("summary", "Peak activity on Monday"));

        mockMvc.perform(post("/reports/ai-summary")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Peak activity on Monday"));
    }

    @Test
    @DisplayName("POST /reports/ai-summary — unauthenticated returns 401")
    void getAISummary_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/reports/ai-summary")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-07"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "SUPPORT")
    @DisplayName("POST /reports/ai-summary — SUPPORT returns 403")
    void getAISummary_Support_Returns403() throws Exception {
        mockMvc.perform(post("/reports/ai-summary")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-07"))
                .andExpect(status().isForbidden());
    }
}
