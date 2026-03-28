package com.teletrack.incidentservice.controller;

import com.teletrack.commonutils.exception.BadRequestException;
import com.teletrack.commonutils.exception.ResourceNotFoundException;
import com.teletrack.incidentservice.config.JwtService;
import com.teletrack.incidentservice.config.TestSecurityConfig;
import com.teletrack.incidentservice.service.IncidentService;
import io.micrometer.core.instrument.MeterRegistry;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("controller")
@WebMvcTest(IncidentController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private static final String TEST_USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired MockMvc mockMvc;

    @MockitoBean JwtService jwtService;
    @MockitoBean IncidentService incidentService;
    @MockitoBean MeterRegistry meterRegistry;

    @Test
    @DisplayName("404 — ResourceNotFoundException returns correct shape")
    @WithMockUser(username = TEST_USER_ID)
    void resourceNotFound_Returns404WithShape() throws Exception {
        UUID id = UUID.randomUUID();
        when(incidentService.getIncidentById(any()))
                .thenThrow(new ResourceNotFoundException("Incident not found with id: " + id));

        mockMvc.perform(get("/incidents/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").exists());
    }

    @Test
    @DisplayName("400 — BadRequestException returns correct shape")
    @WithMockUser(username = TEST_USER_ID)
    void badRequest_Returns400WithShape() throws Exception {
        UUID id = UUID.randomUUID();
        when(incidentService.getIncidentById(any()))
                .thenThrow(new BadRequestException("Invalid operation"));

        mockMvc.perform(get("/incidents/{id}", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid operation"));
    }

    @Test
    @DisplayName("500 — unexpected exception returns generic error shape")
    @WithMockUser(username = TEST_USER_ID)
    void genericException_Returns500WithShape() throws Exception {
        UUID id = UUID.randomUUID();
        when(incidentService.getIncidentById(any()))
                .thenThrow(new RuntimeException("Something went wrong"));

        mockMvc.perform(get("/incidents/{id}", id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    @DisplayName("403 — AccessDeniedException not swallowed by catch-all")
    @WithMockUser(username = TEST_USER_ID, roles = "OPERATOR")
    void accessDenied_Returns403NotSwallowed() throws Exception {
        mockMvc.perform(get("/incidents"))
                .andExpect(status().isForbidden());
    }
}
