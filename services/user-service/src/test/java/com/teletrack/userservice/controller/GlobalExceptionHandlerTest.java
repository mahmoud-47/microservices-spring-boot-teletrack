package com.teletrack.userservice.controller;

import com.teletrack.userservice.config.TestSecurityConfig;
import com.teletrack.userservice.exception.GlobalExceptionHandler;
import com.teletrack.userservice.security.CustomUserDetailsService;
import com.teletrack.userservice.security.JwtService;
import com.teletrack.userservice.security.OAuth2LoginSuccessHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TestExceptionController.class)
@Import({GlobalExceptionHandler.class, TestSecurityConfig.class})
@ActiveProfiles("test")
@Tag("controller")
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    // ─── Tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return 400 with field errors for validation failure")
    void handleValidationExceptions_Returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/test-exceptions/validation")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("Should return 401 for BadCredentialsException")
    void handleBadCredentials_Returns401() throws Exception {
        mockMvc.perform(get("/test-exceptions/bad-credentials")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("Should return 401 for AuthenticationException")
    void handleAuthenticationException_Returns401() throws Exception {
        mockMvc.perform(get("/test-exceptions/auth-exception")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message").value("Authentication failed"))
                .andExpect(jsonPath("$.error").value("Insufficient auth"));
    }

    @Test
    @DisplayName("Should return 404 for ResourceNotFoundException")
    void handleResourceNotFoundException_Returns404() throws Exception {
        mockMvc.perform(get("/test-exceptions/not-found")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    @DisplayName("Should return 400 for BadRequestException")
    void handleBadRequestException_Returns400() throws Exception {
        mockMvc.perform(get("/test-exceptions/bad-request")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    @DisplayName("Should return 500 for unexpected RuntimeException")
    void handleGlobalException_Returns500() throws Exception {
        mockMvc.perform(get("/test-exceptions/unexpected")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
