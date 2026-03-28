package com.teletrack.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teletrack.commonutils.dto.request.UpdateUserRequest;
import com.teletrack.commonutils.dto.response.ApiResponse;
import com.teletrack.commonutils.dto.response.PageResponse;
import com.teletrack.commonutils.dto.response.UserResponse;
import com.teletrack.commonutils.exception.BadRequestException;
import com.teletrack.commonutils.exception.ResourceNotFoundException;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.security.CustomUserDetailsService;
import com.teletrack.userservice.security.JwtService;
import com.teletrack.userservice.security.OAuth2LoginSuccessHandler;
import com.teletrack.userservice.config.TestSecurityConfig;
import com.teletrack.userservice.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(com.teletrack.userservice.controller.UserController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@Tag("controller")
@DisplayName("UserController Tests")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    private static final UUID USER_ID = UUID.randomUUID();

    private UserResponse stubUser() {
        return UserResponse.builder()
                .id(USER_ID.toString())
                .email("op@example.com")
                .role("OPERATOR")
                .isActive(true)
                .isApproved(true)
                .build();
    }

    @SuppressWarnings("unchecked")
    private PageResponse<UserResponse> stubPage() {
        return PageResponse.<UserResponse>builder()
                .content(List.of(stubUser()))
                .page(0).size(20).totalElements(1L).totalPages(1)
                .first(true).last(true).empty(false).build();
    }

    // ─── GET /users/{id} ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /users/{id} - ADMIN can fetch any user")
    void getUserById_AdminRole_Returns200() throws Exception {
        when(userService.getUserById(USER_ID)).thenReturn(stubUser());

        mockMvc.perform(get("/users/{id}", USER_ID)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()));
    }

    @Test
    @DisplayName("GET /users/{id} - OPERATOR role is allowed")
    void getUserById_OperatorRole_Returns200() throws Exception {
        when(userService.getUserById(USER_ID)).thenReturn(stubUser());

        mockMvc.perform(get("/users/{id}", USER_ID)
                        .with(user("op@example.com").roles("OPERATOR")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /users/{id} - unauthenticated returns 401")
    void getUserById_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/users/{id}", USER_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /users/{id} - not found returns 404")
    void getUserById_NotFound_Returns404() throws Exception {
        when(userService.getUserById(any()))
                .thenThrow(new ResourceNotFoundException("User not found with id: " + USER_ID));

        mockMvc.perform(get("/users/{id}", USER_ID)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    // ─── GET /users ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /users - ADMIN gets paginated result")
    void getAllUsers_AdminRole_Returns200() throws Exception {
        when(userService.getAllUsers(any(), any())).thenReturn(stubPage());

        mockMvc.perform(get("/users")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    @DisplayName("GET /users - OPERATOR returns 403")
    void getAllUsers_OperatorRole_Returns403() throws Exception {
        mockMvc.perform(get("/users")
                        .with(user("op@example.com").roles("OPERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /users - with role filter passes through to service")
    void getAllUsers_WithRoleFilter_PassesRoleToService() throws Exception {
        when(userService.getAllUsers(eq(UserRole.OPERATOR), any())).thenReturn(stubPage());

        mockMvc.perform(get("/users")
                        .param("role", "OPERATOR")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    // ─── PUT /users/{id} ──────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /users/{id} - ADMIN can update user")
    void updateUser_AdminRole_Returns200() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest("New", "Name");
        when(userService.updateUser(eq(USER_ID), any())).thenReturn(stubUser());

        mockMvc.perform(put("/users/{id}", USER_ID)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /users/{id} - service throws BadRequestException returns 400")
    void updateUser_NoFields_Returns400() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest(null, null);
        when(userService.updateUser(any(), any()))
                .thenThrow(new BadRequestException("No valid fields provided for update"));

        mockMvc.perform(put("/users/{id}", USER_ID)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ─── PATCH /users/{id}/approve ────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /users/{id}/approve - ADMIN succeeds")
    void approveUser_AdminRole_Returns200() throws Exception {
        when(userService.approveUser(USER_ID)).thenReturn(
                ApiResponse.builder().success(true).message("User approved successfully").build());

        mockMvc.perform(patch("/users/{id}/approve", USER_ID)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("PATCH /users/{id}/approve - OPERATOR returns 403")
    void approveUser_OperatorRole_Returns403() throws Exception {
        mockMvc.perform(patch("/users/{id}/approve", USER_ID)
                        .with(user("op@example.com").roles("OPERATOR")))
                .andExpect(status().isForbidden());
    }

    // ─── PATCH /users/{id}/deactivate ─────────────────────────────────────────

    @Test
    @DisplayName("PATCH /users/{id}/deactivate - ADMIN succeeds")
    void deactivateUser_AdminRole_Returns200() throws Exception {
        when(userService.deactivateUser(USER_ID)).thenReturn(
                ApiResponse.builder().success(true).message("User deactivated successfully").build());

        mockMvc.perform(patch("/users/{id}/deactivate", USER_ID)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    // ─── PATCH /users/{id}/activate ───────────────────────────────────────────

    @Test
    @DisplayName("PATCH /users/{id}/activate - ADMIN succeeds")
    void activateUser_AdminRole_Returns200() throws Exception {
        when(userService.activateUser(USER_ID)).thenReturn(
                ApiResponse.builder().success(true).message("User activated successfully").build());

        mockMvc.perform(patch("/users/{id}/activate", USER_ID)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    // ─── DELETE /users/{id} ───────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /users/{id} - ADMIN succeeds")
    void deleteUser_AdminRole_Returns200() throws Exception {
        when(userService.deleteUser(USER_ID)).thenReturn(
                ApiResponse.builder().success(true).message("User deleted successfully").build());

        mockMvc.perform(delete("/users/{id}", USER_ID)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /users/{id} - self-delete returns 400")
    void deleteUser_SelfDelete_Returns400() throws Exception {
        when(userService.deleteUser(any()))
                .thenThrow(new BadRequestException("Cannot delete your own account"));

        mockMvc.perform(delete("/users/{id}", USER_ID)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    // ─── PATCH /users/{id}/role ───────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /users/{id}/role - ADMIN changes role")
    void changeUserRole_AdminRole_Returns200() throws Exception {
        when(userService.changeUserRole(USER_ID, UserRole.SUPPORT)).thenReturn(stubUser());

        mockMvc.perform(patch("/users/{id}/role", USER_ID)
                        .param("role", "SUPPORT")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    // ─── GET /users/validate/{id} ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /users/validate/{id} - SERVICE role returns boolean result")
    void validateUser_ServiceRole_Returns200() throws Exception {
        when(userService.validateUser(USER_ID)).thenReturn(Boolean.TRUE);

        mockMvc.perform(get("/users/validate/{id}", USER_ID)
                        .with(user("incident-service").roles("SERVICE")))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("GET /users/validate/{id} - ADMIN role returns 403 (SERVICE only)")
    void validateUser_AdminRole_Returns403() throws Exception {
        mockMvc.perform(get("/users/validate/{id}", USER_ID)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }
}
