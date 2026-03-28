package com.teletrack.reportingservice.contract;

import com.teletrack.reportingservice.document.IncidentReport;
import com.teletrack.reportingservice.integration.BaseIntegrationTest;
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

/**
 * Contract tests verifying the shape of reporting-service API responses consumed
 * by frontend clients and dashboards.
 */
@Tag("contract")
@DisplayName("Reporting Service Contract Tests")
class ReportingContractTest extends BaseIntegrationTest {

    @Autowired
    private IncidentReportRepository reportRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void seedData() {
        reportRepository.deleteAll();
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });

        UUID userId = UUID.randomUUID();
        reportRepository.save(IncidentReport.builder()
                .incidentId(UUID.randomUUID())
                .title("Contract Test Incident")
                .status("RESOLVED")
                .priority("HIGH")
                .reportedBy(userId)
                .assignedTo(userId)
                .createdAt(LocalDateTime.now().minusHours(2))
                .resolvedAt(LocalDateTime.now())
                .resolutionTimeMinutes(120L)
                .build());
    }

    // ─── Contract: GET /reports/summary ───────────────────────────────────────

    @Test
    @DisplayName("contract: GET /reports/summary — response has required fields")
    void getSummary_ContractFields() {
        given()
                .header("Authorization", "Bearer " + adminJwt())
                .when().get("/reports/summary")
                .then()
                .statusCode(200)
                .body("totalIncidents", notNullValue())
                .body("byStatus", notNullValue())
                .body("byPriority", notNullValue())
                .body("averageResolutionTimeMinutes", notNullValue());
    }

    @Test
    @DisplayName("contract: GET /reports/summary — totalIncidents is a number")
    void getSummary_TotalIncidentsIsNumber() {
        given()
                .header("Authorization", "Bearer " + adminJwt())
                .when().get("/reports/summary")
                .then()
                .statusCode(200)
                .body("totalIncidents", isA(Integer.class));
    }

    @Test
    @DisplayName("contract: GET /reports/summary — byStatus contains seeded status")
    void getSummary_ByStatusContainsResolvedKey() {
        given()
                .header("Authorization", "Bearer " + adminJwt())
                .when().get("/reports/summary")
                .then()
                .statusCode(200)
                .body("byStatus.RESOLVED", is(1));
    }

    // ─── Contract: GET /reports/trends ────────────────────────────────────────

    @Test
    @DisplayName("contract: GET /reports/trends — response has required fields")
    void getTrends_ContractFields() {
        String today = LocalDateTime.now().toLocalDate().toString();

        given()
                .header("Authorization", "Bearer " + adminJwt())
                .param("startDate", today)
                .param("endDate", today)
                .when().get("/reports/trends")
                .then()
                .statusCode(200)
                .body("period", notNullValue())
                .body("startDate", notNullValue())
                .body("endDate", notNullValue())
                .body("dailyCounts", notNullValue());
    }

    // ─── Contract: GET /reports/user-performance ──────────────────────────────

    @Test
    @DisplayName("contract: GET /reports/user-performance — list items have required fields")
    void getUserPerformance_ContractFields() {
        given()
                .header("Authorization", "Bearer " + adminJwt())
                .when().get("/reports/user-performance")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("[0].userId", notNullValue())
                .body("[0].incidentsAssigned", notNullValue())
                .body("[0].incidentsResolved", notNullValue())
                .body("[0].averageResolutionTimeMinutes", notNullValue());
    }

    // ─── Contract: GET /reports/user-performance/{userId} ────────────────────

    @Test
    @DisplayName("contract: GET /reports/user-performance/{userId} — response has required fields")
    void getUserPerformanceById_ContractFields() {
        UUID userId = reportRepository.findAll().get(0).getAssignedTo();

        given()
                .header("Authorization", "Bearer " + adminJwt())
                .when().get("/reports/user-performance/{userId}", userId.toString())
                .then()
                .statusCode(200)
                .body("userId", is(userId.toString()))
                .body("incidentsAssigned", isA(Integer.class))
                .body("incidentsResolved", isA(Integer.class))
                .body("averageResolutionTimeMinutes", isA(Float.class));
    }

    // ─── Contract: security (frontend expects 401 shape) ─────────────────────

    @Test
    @DisplayName("contract: unauthenticated request returns 401 with expected JSON shape")
    void unauthenticated_Returns401WithJsonShape() {
        given()
                .when().get("/reports/summary")
                .then()
                .statusCode(401)
                .body("success", is(false))
                .body("message", notNullValue())
                .body("path", is("/reports/summary"));
    }
}
