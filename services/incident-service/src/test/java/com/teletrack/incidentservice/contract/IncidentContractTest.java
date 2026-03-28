package com.teletrack.incidentservice.contract;

import com.teletrack.commonutils.enums.IncidentPriority;
import com.teletrack.commonutils.enums.IncidentStatus;
import com.teletrack.incidentservice.entity.Incident;
import com.teletrack.incidentservice.integration.BaseIntegrationTest;
import com.teletrack.incidentservice.repository.IncidentHistoryRepository;
import com.teletrack.incidentservice.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Contract tests verifying the shape of incident-service API responses
 * consumed by frontend clients, reporting-service, and notification-service.
 */
@Tag("contract")
@DisplayName("Incident Service Contract Tests")
class IncidentContractTest extends BaseIntegrationTest {

    @Autowired private IncidentRepository incidentRepository;
    @Autowired private IncidentHistoryRepository incidentHistoryRepository;

    private Incident savedIncident;

    @BeforeEach
    void seedData() {
        incidentHistoryRepository.deleteAll();
        incidentRepository.deleteAll();

        when(userServiceClient.validateUser(any())).thenReturn(true);
        when(userServiceClient.getUserById(any())).thenReturn(null);

        savedIncident = incidentRepository.save(Incident.builder()
                .title("Contract Test Incident")
                .description("Verifying API contract shape")
                .status(IncidentStatus.OPEN)
                .priority(IncidentPriority.HIGH)
                .reportedBy(UUID.randomUUID())
                .createdAt(LocalDateTime.now().minusHours(1))
                .build());
    }

    // ─── Contract: GET /incidents/{id} ────────────────────────────────────────

    @Test
    @DisplayName("contract: GET /incidents/{id} — response has required fields")
    void getIncidentById_ContractFields() {
        given()
                .header("Authorization", "Bearer " + adminJwt())
                .when().get("/incidents/{id}", savedIncident.getId())
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", notNullValue())
                .body("data.id", notNullValue())
                .body("data.title", notNullValue())
                .body("data.status", notNullValue())
                .body("data.priority", notNullValue())
                .body("data.reportedBy", notNullValue())
                .body("data.history", notNullValue());
    }

    // ─── Contract: GET /incidents ─────────────────────────────────────────────

    @Test
    @DisplayName("contract: GET /incidents — PageResponse has required fields")
    void getAllIncidents_ContractFields() {
        given()
                .header("Authorization", "Bearer " + adminJwt())
                .when().get("/incidents")
                .then()
                .statusCode(200)
                .body("data.content", notNullValue())
                .body("data.page", notNullValue())
                .body("data.size", notNullValue())
                .body("data.totalElements", notNullValue())
                .body("data.totalPages", notNullValue())
                .body("data.last", notNullValue())
                .body("data.first", notNullValue());
    }

    // ─── Contract: POST /incidents ────────────────────────────────────────────

    @Test
    @DisplayName("contract: POST /incidents — created response has required fields")
    void createIncident_ContractFields() {
        String body = """
                {
                  "title": "Contract Create Test",
                  "description": "A long enough description for the contract test",
                  "priority": "MEDIUM"
                }
                """;

        given()
                .header("Authorization", "Bearer " + operatorJwt())
                .contentType("application/json")
                .body(body)
                .when().post("/incidents")
                .then()
                .statusCode(201)
                .body("success", is(true))
                .body("message", notNullValue())
                .body("data.id", notNullValue())
                .body("data.status", is("OPEN"))
                .body("data.priority", is("MEDIUM"));
    }

    // ─── Contract: security (frontend expects 401 shape) ─────────────────────

    @Test
    @DisplayName("contract: unauthenticated request returns 401 with expected JSON shape")
    void unauthenticated_Returns401WithJsonShape() {
        given()
                .when().get("/incidents/{id}", savedIncident.getId())
                .then()
                .statusCode(401)
                .body("success", is(false))
                .body("message", notNullValue())
                .body("path", is("/incidents/" + savedIncident.getId()));
    }

    // ─── Contract: reporting-service consumer contract ────────────────────────

    @Test
    @DisplayName("contract: incident content matches reporting-service expected fields")
    void incidentContent_ReportingServiceFields() {
        given()
                .header("Authorization", "Bearer " + adminJwt())
                .when().get("/incidents/{id}", savedIncident.getId())
                .then()
                .statusCode(200)
                .body("data.status", isA(String.class))
                .body("data.priority", isA(String.class))
                .body("data.reportedBy", isA(String.class))
                .body("data.createdAt", notNullValue());
    }
}
