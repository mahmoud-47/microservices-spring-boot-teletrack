package com.teletrack.incidentservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teletrack.commonutils.dto.request.AssignIncidentRequest;
import com.teletrack.commonutils.dto.request.CreateIncidentRequest;
import com.teletrack.commonutils.dto.request.UpdateIncidentRequest;
import com.teletrack.commonutils.dto.response.IncidentResponse;
import com.teletrack.commonutils.enums.IncidentPriority;
import com.teletrack.commonutils.enums.IncidentStatus;
import com.teletrack.commonutils.exception.ResourceNotFoundException;
import com.teletrack.incidentservice.config.JwtService;
import com.teletrack.incidentservice.config.TestSecurityConfig;
import com.teletrack.incidentservice.service.IncidentService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("controller")
@WebMvcTest(IncidentController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("IncidentController Tests")
class IncidentControllerTest {

    private static final String TEST_USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean JwtService jwtService;
    @MockitoBean IncidentService incidentService;
    @MockitoBean MeterRegistry meterRegistry;

    private IncidentResponse sampleResponse;
    private Page<IncidentResponse> samplePage;

    @BeforeEach
    void setUp() {
        sampleResponse = new IncidentResponse();
        sampleResponse.setId(UUID.randomUUID());
        sampleResponse.setTitle("Test Incident");
        sampleResponse.setStatus(IncidentStatus.OPEN);
        sampleResponse.setPriority(IncidentPriority.HIGH);

        samplePage = new PageImpl<>(List.of(sampleResponse));
    }

    // ─── POST /incidents ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /incidents — ADMIN creates incident returns 201")
    @WithMockUser(username = TEST_USER_ID, roles = "ADMIN")
    void createIncident_Admin_Returns201() throws Exception {
        CreateIncidentRequest request = new CreateIncidentRequest(
                "Valid Title Here", "A valid description that is long enough", IncidentPriority.HIGH);
        when(incidentService.createIncident(any(), any())).thenReturn(sampleResponse);

        mockMvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("POST /incidents — OPERATOR can create incident")
    @WithMockUser(username = TEST_USER_ID, roles = "OPERATOR")
    void createIncident_Operator_Returns201() throws Exception {
        CreateIncidentRequest request = new CreateIncidentRequest(
                "Valid Title Here", "A valid description that is long enough", IncidentPriority.MEDIUM);
        when(incidentService.createIncident(any(), any())).thenReturn(sampleResponse);

        mockMvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /incidents — unauthenticated returns 401")
    void createIncident_Unauthenticated_Returns401() throws Exception {
        CreateIncidentRequest request = new CreateIncidentRequest(
                "Valid Title Here", "A valid description that is long enough", IncidentPriority.HIGH);

        mockMvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /incidents — validation failure returns 400")
    @WithMockUser(username = TEST_USER_ID, roles = "ADMIN")
    void createIncident_BlankTitle_Returns400() throws Exception {
        CreateIncidentRequest request = new CreateIncidentRequest("", "A valid description that is long enough", IncidentPriority.HIGH);

        mockMvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── GET /incidents/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /incidents/{id} — authenticated returns 200")
    @WithMockUser(username = TEST_USER_ID)
    void getIncidentById_Found_Returns200() throws Exception {
        UUID id = sampleResponse.getId();
        when(incidentService.getIncidentById(id)).thenReturn(sampleResponse);

        mockMvc.perform(get("/incidents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /incidents/{id} — not found returns 404")
    @WithMockUser(username = TEST_USER_ID)
    void getIncidentById_NotFound_Returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(incidentService.getIncidentById(id))
                .thenThrow(new ResourceNotFoundException("Incident not found with id: " + id));

        mockMvc.perform(get("/incidents/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /incidents/{id} — unauthenticated returns 401")
    void getIncidentById_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/incidents/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /incidents ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /incidents — ADMIN returns 200 with page data")
    @WithMockUser(username = TEST_USER_ID, roles = "ADMIN")
    void getAllIncidents_Admin_Returns200() throws Exception {
        when(incidentService.getAllIncidents(any())).thenReturn(samplePage);

        mockMvc.perform(get("/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /incidents — SUPPORT returns 200")
    @WithMockUser(username = TEST_USER_ID, roles = "SUPPORT")
    void getAllIncidents_Support_Returns200() throws Exception {
        when(incidentService.getAllIncidents(any())).thenReturn(samplePage);

        mockMvc.perform(get("/incidents"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /incidents — OPERATOR returns 403")
    @WithMockUser(username = TEST_USER_ID, roles = "OPERATOR")
    void getAllIncidents_Operator_Returns403() throws Exception {
        mockMvc.perform(get("/incidents"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /incidents — unauthenticated returns 401")
    void getAllIncidents_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/incidents"))
                .andExpect(status().isUnauthorized());
    }

    // ─── PUT /incidents/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /incidents/{id} — ADMIN updates returns 200")
    @WithMockUser(username = TEST_USER_ID, roles = "ADMIN")
    void updateIncident_Admin_Returns200() throws Exception {
        UUID id = sampleResponse.getId();
        UpdateIncidentRequest request = new UpdateIncidentRequest("Updated Title", null, null, null);
        when(incidentService.updateIncident(eq(id), any(), any())).thenReturn(sampleResponse);

        mockMvc.perform(put("/incidents/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PUT /incidents/{id} — unauthenticated returns 401")
    void updateIncident_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(put("/incidents/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ─── POST /incidents/{id}/assign ─────────────────────────────────────────

    @Test
    @DisplayName("POST /incidents/{id}/assign — ADMIN assigns returns 200")
    @WithMockUser(username = TEST_USER_ID, roles = "ADMIN")
    void assignIncident_Admin_Returns200() throws Exception {
        UUID id = sampleResponse.getId();
        AssignIncidentRequest request = new AssignIncidentRequest(UUID.randomUUID());
        when(incidentService.assignIncident(eq(id), any(), any())).thenReturn(sampleResponse);

        mockMvc.perform(post("/incidents/{id}/assign", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident assigned successfully"));
    }

    @Test
    @DisplayName("POST /incidents/{id}/assign — OPERATOR returns 403")
    @WithMockUser(username = TEST_USER_ID, roles = "OPERATOR")
    void assignIncident_Operator_Returns403() throws Exception {
        mockMvc.perform(post("/incidents/{id}/assign", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignIncidentRequest(UUID.randomUUID()))))
                .andExpect(status().isForbidden());
    }

    // ─── PUT /incidents/{id}/status ──────────────────────────────────────────

    @Test
    @DisplayName("PUT /incidents/{id}/status — ADMIN changes status returns 200")
    @WithMockUser(username = TEST_USER_ID, roles = "ADMIN")
    void changeStatus_Admin_Returns200() throws Exception {
        UUID id = sampleResponse.getId();
        when(incidentService.changeStatus(eq(id), eq(IncidentStatus.RESOLVED), any()))
                .thenReturn(sampleResponse);

        mockMvc.perform(put("/incidents/{id}/status", id)
                        .param("status", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PUT /incidents/{id}/status — unauthenticated returns 401")
    void changeStatus_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(put("/incidents/{id}/status", UUID.randomUUID())
                        .param("status", "RESOLVED"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /incidents/my ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /incidents/my — authenticated returns 200")
    @WithMockUser(username = TEST_USER_ID, roles = "OPERATOR")
    void getMyIncidents_Authenticated_Returns200() throws Exception {
        when(incidentService.getIncidentsByReportedBy(any(), any())).thenReturn(samplePage);

        mockMvc.perform(get("/incidents/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /incidents/my — unauthenticated returns 401")
    void getMyIncidents_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/incidents/my"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /incidents/assigned ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /incidents/assigned — SUPPORT returns 200")
    @WithMockUser(username = TEST_USER_ID, roles = "SUPPORT")
    void getAssignedIncidents_Support_Returns200() throws Exception {
        when(incidentService.getIncidentsByAssignedTo(any(), any())).thenReturn(samplePage);

        mockMvc.perform(get("/incidents/assigned"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /incidents/assigned — OPERATOR returns 403")
    @WithMockUser(username = TEST_USER_ID, roles = "OPERATOR")
    void getAssignedIncidents_Operator_Returns403() throws Exception {
        mockMvc.perform(get("/incidents/assigned"))
                .andExpect(status().isForbidden());
    }

    // ─── GET /incidents/status/{status} ──────────────────────────────────────

    @Test
    @DisplayName("GET /incidents/status/{status} — ADMIN filters by status")
    @WithMockUser(username = TEST_USER_ID, roles = "ADMIN")
    void getIncidentsByStatus_Admin_Returns200() throws Exception {
        when(incidentService.getIncidentsByStatus(eq(IncidentStatus.OPEN), any()))
                .thenReturn(samplePage);

        mockMvc.perform(get("/incidents/status/{status}", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /incidents/status/{status} — unauthenticated returns 401")
    void getIncidentsByStatus_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/incidents/status/{status}", "OPEN"))
                .andExpect(status().isUnauthorized());
    }
}
