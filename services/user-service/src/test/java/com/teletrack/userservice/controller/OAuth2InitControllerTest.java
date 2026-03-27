package com.teletrack.userservice.controller;

import com.teletrack.userservice.security.CustomUserDetailsService;
import com.teletrack.userservice.security.JwtService;
import com.teletrack.userservice.security.OAuth2LoginSuccessHandler;
import com.teletrack.userservice.config.TestSecurityConfig;
import com.teletrack.userservice.service.OAuthStateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(com.teletrack.userservice.controller.OAuth2InitController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@Tag("controller")
@DisplayName("OAuth2InitController Tests")
class OAuth2InitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OAuthStateService oAuthStateService;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Test
    @DisplayName("GET /auth/oauth/google?role=OPERATOR - valid role causes redirect")
    void initiateOAuth_ValidOperatorRole_Redirects() throws Exception {
        when(oAuthStateService.createState(any())).thenReturn("test-state-token");

        mockMvc.perform(get("/auth/oauth/google").param("role", "OPERATOR"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString("test-state-token")));
    }

    @Test
    @DisplayName("GET /auth/oauth/google?role=ADMIN - valid role causes redirect")
    void initiateOAuth_ValidAdminRole_Redirects() throws Exception {
        when(oAuthStateService.createState(any())).thenReturn("admin-state-token");

        mockMvc.perform(get("/auth/oauth/google").param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("GET /auth/oauth/google?role=SUPPORT - valid role causes redirect")
    void initiateOAuth_ValidSupportRole_Redirects() throws Exception {
        when(oAuthStateService.createState(any())).thenReturn("support-state-token");

        mockMvc.perform(get("/auth/oauth/google").param("role", "SUPPORT"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("GET /auth/oauth/google?role=INVALID - bad role returns 400")
    void initiateOAuth_InvalidRole_Returns400() throws Exception {
        mockMvc.perform(get("/auth/oauth/google").param("role", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Role not correct"));
    }

    @Test
    @DisplayName("GET /auth/oauth/google?role=operator - lowercase is accepted (toUpperCase)")
    void initiateOAuth_LowercaseRole_Redirects() throws Exception {
        when(oAuthStateService.createState(any())).thenReturn("state-token");

        mockMvc.perform(get("/auth/oauth/google").param("role", "operator"))
                .andExpect(status().is3xxRedirection());
    }
}
