package com.teletrack.incidentservice.integration;

import com.teletrack.commonutils.enums.IncidentPriority;
import com.teletrack.commonutils.enums.IncidentStatus;
import com.teletrack.incidentservice.entity.Incident;
import com.teletrack.incidentservice.entity.IncidentHistory;
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

@Tag("integration")
@DisplayName("Incident Integration Tests")
class IncidentIntegrationTest extends BaseIntegrationTest {

    @Autowired private IncidentRepository incidentRepository;
    @Autowired private IncidentHistoryRepository incidentHistoryRepository;

    private Incident savedIncident;
    private UUID operatorId;

    @BeforeEach
    void setUp() {
        incidentHistoryRepository.deleteAll();
        incidentRepository.deleteAll();

        // Stub user-service calls so Feign doesn't fail
        when(userServiceClient.validateUser(any())).thenReturn(true);
        when(userServiceClient.getUserById(any())).thenReturn(null);

        operatorId = UUID.randomUUID();
        savedIncident = incidentRepository.save(Incident.builder()
                .title("Network Outage")
                .description("Core router unreachable")
                .status(IncidentStatus.OPEN)
                .priority(IncidentPriority.CRITICAL)
                .reportedBy(operatorId)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build());
    }

    // ─── GET /incidents/hello ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /incidents/hello — public endpoint returns 200")
    void hello_Returns200() {
        given()
                .when().get("/incidents/hello")
                .then()
                .statusCode(200);
    }

    // ─── GET /incidents/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /incidents/{id} — authenticated returns 200 with incident data")
    void getIncidentById_Authenticated_Returns200() {
        given()
                .header("Authorization", "Bearer " + adminJwt())
                .when().get("/incidents/{id}", savedIncident.getId())
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.id", is(savedIncident.getId().toString()))
                .body("data.title", is("Network Outage"));
    }

    @Test
    @DisplayName("GET /incidents/{id} — not found returns 404")
    void getIncidentById_NotFound_Returns404() {
        given()
                .header("Authorization", "Bearer " + adminJwt())
                .when().get("/incidents/{id}", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("success", is(false));
    }

    @Test
    @DisplayName("GET /incidents/{id} — unauthenticated returns 401")
    void getIncidentById_Unauthenticated_Returns401() {
        given()
                .when().get("/incidents/{id}", savedIncident.getId())
                .then()
                .statusCode(401)
                .body("success", is(false));
    }

    // ─── GET /incidents ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /incidents — ADMIN returns paginated list")
    void getAllIncidents_Admin_ReturnsList() {
        given()
                .header("Authorization", "Bearer " + adminJwt())
                .when().get("/incidents")
                .then()
                .statusCode(200)
                .body("data.content", hasSize(1))
                .body("data.totalElements", is(1));
    }

    @Test
    @DisplayName("GET /incidents — SUPPORT returns 200")
    void getAllIncidents_Support_Returns200() {
        given()
                .header("Authorization", "Bearer " + supportJwt())
                .when().get("/incidents")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("GET /incidents — OPERATOR returns 403")
    void getAllIncidents_Operator_Returns403() {
        given()
                .header("Authorization", "Bearer " + operatorJwt())
                .when().get("/incidents")
                .then()
                .statusCode(403);
    }

    // ─── POST /incidents ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /incidents — OPERATOR creates incident successfully")
    void createIncident_Operator_Returns201() {
        String body = """
                {
                  "title": "Fiber Cut Detected",
                  "description": "Main fiber line cut between zones A and B",
                  "priority": "HIGH"
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
                .body("data.status", is("OPEN"));
    }

    @Test
    @DisplayName("POST /incidents — validation failure returns 400")
    void createIncident_InvalidBody_Returns400() {
        String body = """
                {
                  "title": "",
                  "description": "short",
                  "priority": "HIGH"
                }
                """;

        given()
                .header("Authorization", "Bearer " + operatorJwt())
                .contentType("application/json")
                .body(body)
                .when().post("/incidents")
                .then()
                .statusCode(400)
                .body("success", is(false));
    }

    @Test
    @DisplayName("POST /incidents — unauthenticated returns 401")
    void createIncident_Unauthenticated_Returns401() {
        given()
                .contentType("application/json")
                .body("{\"title\":\"T\",\"description\":\"D\",\"priority\":\"LOW\"}")
                .when().post("/incidents")
                .then()
                .statusCode(401);
    }

    // ─── PUT /incidents/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /incidents/{id} — ADMIN updates title")
    void updateIncident_Admin_UpdatesTitle() {
        String body = """
                {
                  "title": "Updated Network Outage Title"
                }
                """;

        given()
                .header("Authorization", "Bearer " + adminJwt())
                .contentType("application/json")
                .body(body)
                .when().put("/incidents/{id}", savedIncident.getId())
                .then()
                .statusCode(200)
                .body("data.title", is("Updated Network Outage Title"));
    }

    // ─── POST /incidents/{id}/assign ─────────────────────────────────────────

    @Test
    @DisplayName("POST /incidents/{id}/assign — invalid user returns 400")
    void assignIncident_InvalidUser_Returns400() {
        when(userServiceClient.validateUser(any())).thenReturn(false);

        given()
                .header("Authorization", "Bearer " + adminJwt())
                .contentType("application/json")
                .body("{\"assignedTo\":\"" + UUID.randomUUID() + "\"}")
                .when().post("/incidents/{id}/assign", savedIncident.getId())
                .then()
                .statusCode(400)
                .body("success", is(false))
                .body("message", is("User not found"));
    }

    @Test
    @DisplayName("POST /incidents/{id}/assign — ADMIN assigns to user")
    void assignIncident_Admin_AssignsSuccessfully() {
        UUID assigneeId = UUID.randomUUID();

        given()
                .header("Authorization", "Bearer " + adminJwt())
                .contentType("application/json")
                .body("{\"assignedTo\":\"" + assigneeId + "\"}")
                .when().post("/incidents/{id}/assign", savedIncident.getId())
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.assignedTo", is(assigneeId.toString()));
    }

    // ─── PUT /incidents/{id}/status ──────────────────────────────────────────

    @Test
    @DisplayName("PUT /incidents/{id}/status — ADMIN resolves incident")
    void changeStatus_Admin_Resolves() {
        given()
                .header("Authorization", "Bearer " + adminJwt())
                .param("status", "RESOLVED")
                .when().put("/incidents/{id}/status", savedIncident.getId())
                .then()
                .statusCode(200)
                .body("data.status", is("RESOLVED"));
    }

    @Test
    @DisplayName("PUT /incidents/{id}/status — OPERATOR returns 403")
    void changeStatus_Operator_Returns403() {
        given()
                .header("Authorization", "Bearer " + operatorJwt())
                .param("status", "RESOLVED")
                .when().put("/incidents/{id}/status", savedIncident.getId())
                .then()
                .statusCode(403);
    }

    // ─── GET /incidents/my ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /incidents/my — returns incidents reported by current user")
    void getMyIncidents_ReturnsOwnIncidents() {
        String jwt = generateJwt(operatorId.toString(), "OPERATOR");

        given()
                .header("Authorization", "Bearer " + jwt)
                .when().get("/incidents/my")
                .then()
                .statusCode(200)
                .body("data.content", hasSize(1));
    }

    @Test
    @DisplayName("GET /incidents/my — other user sees empty list")
    void getMyIncidents_OtherUser_SeesEmpty() {
        given()
                .header("Authorization", "Bearer " + operatorJwt())
                .when().get("/incidents/my")
                .then()
                .statusCode(200)
                .body("data.content", hasSize(0));
    }

    // ─── GET /incidents/status/{status} ──────────────────────────────────────

    @Test
    @DisplayName("GET /incidents/status/{status} — filters correctly")
    void getIncidentsByStatus_Open_ReturnsOne() {
        given()
                .header("Authorization", "Bearer " + adminJwt())
                .when().get("/incidents/status/{status}", "OPEN")
                .then()
                .statusCode(200)
                .body("data.totalElements", is(1));
    }

    @Test
    @DisplayName("GET /incidents/status/{status} — RESOLVED returns empty")
    void getIncidentsByStatus_Resolved_ReturnsEmpty() {
        given()
                .header("Authorization", "Bearer " + adminJwt())
                .when().get("/incidents/status/{status}", "RESOLVED")
                .then()
                .statusCode(200)
                .body("data.totalElements", is(0));
    }
}
