package com.teletrack.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teletrack.commonutils.dto.request.EmailVerificationRequest;
import com.teletrack.commonutils.dto.request.LoginRequest;
import com.teletrack.commonutils.dto.request.RefreshTokenRequest;
import com.teletrack.commonutils.dto.request.RegisterRequest;
import com.teletrack.commonutils.dto.response.ApiResponse;
import com.teletrack.commonutils.dto.response.AuthResponse;
import com.teletrack.commonutils.dto.response.UserResponse;
import com.teletrack.commonutils.exception.BadRequestException;
import com.teletrack.commonutils.exception.ResourceNotFoundException;
import com.teletrack.userservice.security.CustomUserDetailsService;
import com.teletrack.userservice.security.JwtService;
import com.teletrack.userservice.security.OAuth2LoginSuccessHandler;
import com.teletrack.userservice.config.TestSecurityConfig;
import com.teletrack.userservice.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(com.teletrack.userservice.controller.AuthController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@Tag("controller")
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    // ─── POST /auth/register ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/register - success returns 200 with success=true")
    void register_Success_Returns200() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("password123")
                .role(RegisterRequest.Role.OPERATOR)
                .build();

        when(authService.register(any())).thenReturn(
                ApiResponse.builder().success(true)
                        .message("Registration successful. Please check your email to verify your account.")
                        .build());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Registration successful")));
    }

    @Test
    @DisplayName("POST /auth/register - validation failure returns 400")
    void register_ValidationFailure_Returns400() throws Exception {
        RegisterRequest invalid = RegisterRequest.builder()
                .firstName("")
                .lastName("Doe")
                .email("not-valid-email")
                .password("short")
                .build();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @DisplayName("POST /auth/register - duplicate email returns 400")
    void register_DuplicateEmail_Returns400() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("John").lastName("Doe").email("john@example.com")
                .password("password123").role(RegisterRequest.Role.OPERATOR).build();

        when(authService.register(any()))
                .thenThrow(new BadRequestException("Email already registered"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Email already registered")));
    }

    // ─── POST /auth/login ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/login - success returns 200 with tokens")
    void login_Success_Returns200() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("john@example.com").password("password123").build();

        when(authService.login(any())).thenReturn(AuthResponse.builder()
                .accessToken("access_token")
                .refreshToken("refresh_token")
                .tokenType("Bearer")
                .build());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access_token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /auth/login - not approved returns 400")
    void login_NotApproved_Returns400() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("john@example.com").password("password123").build();

        when(authService.login(any()))
                .thenThrow(new BadRequestException("not been approved"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/login - bad credentials returns 401")
    void login_BadCredentials_Returns401() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("john@example.com").password("wrong").build();

        when(authService.login(any()))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /auth/email/confirm ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /auth/email/confirm - success returns 200")
    void confirmEmail_Success_Returns200() throws Exception {
        when(authService.verifyEmail("valid-token")).thenReturn(
                ApiResponse.builder().success(true).message("Email verified successfully.").build());

        mockMvc.perform(get("/auth/email/confirm").param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("GET /auth/email/confirm - expired token returns 400")
    void confirmEmail_ExpiredToken_Returns400() throws Exception {
        when(authService.verifyEmail("expired"))
                .thenThrow(new BadRequestException("Verification token has expired"));

        mockMvc.perform(get("/auth/email/confirm").param("token", "expired"))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /auth/email/verify ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/email/verify - success returns 200")
    void resendVerification_Success_Returns200() throws Exception {
        EmailVerificationRequest request = EmailVerificationRequest.builder()
                .email("john@example.com").build();

        when(authService.resendVerificationEmail(any())).thenReturn(
                ApiResponse.builder().success(true).message("Verification email sent.").build());

        mockMvc.perform(post("/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("POST /auth/email/verify - user not found returns 404")
    void resendVerification_UserNotFound_Returns404() throws Exception {
        EmailVerificationRequest request = EmailVerificationRequest.builder()
                .email("nobody@example.com").build();

        when(authService.resendVerificationEmail(any()))
                .thenThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(post("/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // ─── POST /auth/refresh ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/refresh - success returns 200 with new tokens")
    void refreshToken_Success_Returns200() throws Exception {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("old_token").build();

        when(authService.refreshToken(any())).thenReturn(AuthResponse.builder()
                .accessToken("new_access").refreshToken("new_refresh").tokenType("Bearer").build());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new_access"));
    }

    @Test
    @DisplayName("POST /auth/refresh - invalid token returns 400")
    void refreshToken_InvalidToken_Returns400() throws Exception {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("bad_token").build();

        when(authService.refreshToken(any()))
                .thenThrow(new BadRequestException("Invalid refresh token"));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /auth/me ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /auth/me - authenticated returns 200 with user data")
    void getCurrentUser_Authenticated_Returns200() throws Exception {
        when(authService.getCurrentUser()).thenReturn(UserResponse.builder()
                .id("some-uuid").email("john@example.com").role("OPERATOR").build());

        mockMvc.perform(get("/auth/me")
                        .with(user("john@example.com").roles("OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    @DisplayName("GET /auth/me - unauthenticated returns 401")
    void getCurrentUser_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
