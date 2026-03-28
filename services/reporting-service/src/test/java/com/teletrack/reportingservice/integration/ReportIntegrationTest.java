package com.teletrack.reportingservice.integration;

import com.teletrack.reportingservice.document.IncidentReport;
import com.teletrack.reportingservice.repository.IncidentReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

import java.time.LocalDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Tag("integration")
@DisplayName("Report Integration Tests")
class ReportIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IncidentReportRepository reportRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearData() {
        reportRepository.deleteAll();
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
    }

    // ─── GET /reports/hello ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /reports/hello — public endpoint returns 200")
    void hello_ReturnsOk() {
        given()
                .when().get("/reports/hello")
                .then().statusCode(200);
    }

    // ─── GET /reports/summary ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /reports/summary — unauthenticated returns 401")
    void getSummary_Unauthenticated_Returns401() {
        given()
                .when().get("/reports/summary")
                .then()
                .statusCode(401)
                .body("success", is(false));
    }

    @Test
    @DisplayName("GET /reports/summary — ADMIN with data returns correct totals")
    void getSummary_Admin_ReturnsCorrectData() {
        reportRepository.save(IncidentReport.builder()
                .incidentId(UUID.randomUUID()).status("OPEN").priority("HIGH")
                .createdAt(LocalDateTime.now()).build());
        reportRepository.save(IncidentReport.builder()
                .incidentId(UUID.randomUUID()).status("RESOLVED").priority("MEDIUM")
                .resolutionTimeMinutes(60L).createdAt(LocalDateTime.now()).build());

        given()
                .header("Authorization", "Bearer " + adminJwt())
                .when().get("/reports/summary")
                .then()
                .statusCode(200)
                .body("totalIncidents", is(2))
                .body("byStatus.OPEN", is(1))
                .body("byStatus.RESOLVED", is(1))
                .body("byPriority.HIGH", is(1));
    }

    @Test
    @DisplayName("GET /reports/summary — OPERATOR can access")
    void getSummary_Operator_Returns200() {
        given()
                .header("Authorization", "Bearer " + operatorJwt())
                .when().get("/reports/summary")
                .then()
                .statusCode(200)
                .body("totalIncidents", is(0));
    }

    @Test
    @DisplayName("GET /reports/summary — SUPPORT returns 403")
    void getSummary_Support_Returns403() {
        String supportJwt = generateJwt(UUID.randomUUID().toString(), "SUPPORT");

        given()
                .header("Authorization", "Bearer " + supportJwt)
                .when().get("/reports/summary")
                .then()
                .statusCode(403);
    }

    // ─── GET /reports/trends ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /reports/trends — with date range returns trend data")
    void getTrends_Admin_ReturnsTrendData() {
        LocalDateTime now = LocalDateTime.now();
        reportRepository.save(IncidentReport.builder()
                .incidentId(UUID.randomUUID()).status("OPEN").priority("HIGH")
                .createdAt(now).build());

        given()
                .header("Authorization", "Bearer " + adminJwt())
                .param("startDate", now.toLocalDate().toString())
                .param("endDate", now.toLocalDate().toString())
                .when().get("/reports/trends")
                .then()
                .statusCode(200)
                .body("period", notNullValue())
                .body("dailyCounts", notNullValue());
    }

    @Test
    @DisplayName("GET /reports/trends — unauthenticated returns 401")
    void getTrends_Unauthenticated_Returns401() {
        given()
                .param("startDate", "2024-01-01")
                .param("endDate", "2024-01-31")
                .when().get("/reports/trends")
                .then()
                .statusCode(401);
    }

    // ─── GET /reports/user-performance ────────────────────────────────────────

    @Test
    @DisplayName("GET /reports/user-performance — empty DB returns empty list")
    void getUserPerformance_Empty_ReturnsList() {
        given()
                .header("Authorization", "Bearer " + adminJwt())
                .when().get("/reports/user-performance")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    @DisplayName("GET /reports/user-performance — with data returns user metrics")
    void getUserPerformance_WithData_ReturnsMetrics() {
        UUID userId = UUID.randomUUID();
        reportRepository.save(IncidentReport.builder()
                .incidentId(UUID.randomUUID()).assignedTo(userId)
                .status("RESOLVED").priority("HIGH").resolutionTimeMinutes(30L)
                .createdAt(LocalDateTime.now()).build());

        given()
                .header("Authorization", "Bearer " + adminJwt())
                .when().get("/reports/user-performance")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].incidentsAssigned", is(1))
                .body("[0].incidentsResolved", is(1));
    }

    @Test
    @DisplayName("GET /reports/user-performance — unauthenticated returns 401")
    void getUserPerformance_Unauthenticated_Returns401() {
        given()
                .when().get("/reports/user-performance")
                .then()
                .statusCode(401);
    }

    // ─── GET /reports/user-performance/{userId} ───────────────────────────────

    @Test
    @DisplayName("GET /reports/user-performance/{userId} — returns metrics for user")
    void getUserPerformanceById_ReturnsMetrics() {
        UUID userId = UUID.randomUUID();
        reportRepository.save(IncidentReport.builder()
                .incidentId(UUID.randomUUID()).assignedTo(userId)
                .status("RESOLVED").priority("MEDIUM").resolutionTimeMinutes(45L)
                .createdAt(LocalDateTime.now()).build());

        given()
                .header("Authorization", "Bearer " + adminJwt())
                .when().get("/reports/user-performance/{userId}", userId.toString())
                .then()
                .statusCode(200)
                .body("userId", is(userId.toString()))
                .body("incidentsAssigned", is(1))
                .body("incidentsResolved", is(1))
                .body("averageResolutionTimeMinutes", is(45.0f));
    }

    @Test
    @DisplayName("GET /reports/user-performance/{userId} — unknown user returns zeros")
    void getUserPerformanceById_UnknownUser_ReturnsZeros() {
        UUID unknownUserId = UUID.randomUUID();

        given()
                .header("Authorization", "Bearer " + adminJwt())
                .when().get("/reports/user-performance/{userId}", unknownUserId.toString())
                .then()
                .statusCode(200)
                .body("userId", is(unknownUserId.toString()))
                .body("incidentsAssigned", is(0))
                .body("incidentsResolved", is(0));
    }

    @Test
    @DisplayName("GET /reports/user-performance/{userId} — unauthenticated returns 401")
    void getUserPerformanceById_Unauthenticated_Returns401() {
        given()
                .when().get("/reports/user-performance/{userId}", UUID.randomUUID().toString())
                .then()
                .statusCode(401);
    }
}
