package com.teletrack.userservice.controller;

import com.teletrack.userservice.dto.AuditLogResponse;
import com.teletrack.userservice.dto.SystemStatsResponse;
import com.teletrack.commonutils.dto.response.PageResponse;
import com.teletrack.userservice.security.CustomUserDetailsService;
import com.teletrack.userservice.security.JwtService;
import com.teletrack.userservice.security.OAuth2LoginSuccessHandler;
import com.teletrack.userservice.config.TestSecurityConfig;
import com.teletrack.userservice.service.AdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(com.teletrack.userservice.controller.AdminController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@Tag("controller")
@DisplayName("AdminController Tests")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    private PageResponse<AuditLogResponse> emptyPage() {
        return PageResponse.<AuditLogResponse>builder()
                .content(List.of()).page(0).size(20).totalElements(0L)
                .totalPages(0).first(true).last(true).empty(true).build();
    }

    // ─── GET /admin/audit-logs ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /admin/audit-logs - ADMIN with no filters returns 200")
    void getAuditLogs_AdminRole_Returns200() throws Exception {
        when(adminService.getAuditLogs(isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(emptyPage());

        mockMvc.perform(get("/admin/audit-logs")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /admin/audit-logs - OPERATOR returns 403")
    void getAuditLogs_OperatorRole_Returns403() throws Exception {
        mockMvc.perform(get("/admin/audit-logs")
                        .with(user("op@example.com").roles("OPERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin/audit-logs - unauthenticated returns 401")
    void getAuditLogs_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/admin/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /admin/audit-logs - action filter is passed to service")
    void getAuditLogs_WithActionFilter_PassesToService() throws Exception {
        when(adminService.getAuditLogs(isNull(), eq("APPROVE_USER"), isNull(), isNull(), any()))
                .thenReturn(emptyPage());

        mockMvc.perform(get("/admin/audit-logs")
                        .param("action", "APPROVE_USER")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(adminService).getAuditLogs(isNull(), eq("APPROVE_USER"), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("GET /admin/audit-logs - user UUID filter is passed to service")
    void getAuditLogs_WithUserIdFilter_PassesToService() throws Exception {
        UUID userId = UUID.randomUUID();
        when(adminService.getAuditLogs(eq(userId), isNull(), isNull(), isNull(), any()))
                .thenReturn(emptyPage());

        mockMvc.perform(get("/admin/audit-logs")
                        .param("user", userId.toString())
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(adminService).getAuditLogs(eq(userId), isNull(), isNull(), isNull(), any());
    }

    // ─── GET /admin/stats ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /admin/stats - ADMIN gets statistics")
    void getSystemStats_AdminRole_Returns200() throws Exception {
        SystemStatsResponse stats = SystemStatsResponse.builder()
                .totalUsers(100L)
                .activeUsers(70L)
                .pendingApproval(10L)
                .usersByRole(Map.of("ADMIN", 5L, "OPERATOR", 50L, "SUPPORT", 45L))
                .lastUpdated(LocalDateTime.now())
                .build();

        when(adminService.getSystemStats()).thenReturn(stats);

        mockMvc.perform(get("/admin/stats")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers", is(100)));
    }

    @Test
    @DisplayName("GET /admin/stats - SUPPORT returns 403")
    void getSystemStats_SupportRole_Returns403() throws Exception {
        mockMvc.perform(get("/admin/stats")
                        .with(user("support@example.com").roles("SUPPORT")))
                .andExpect(status().isForbidden());
    }
}
