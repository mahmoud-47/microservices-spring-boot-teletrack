package com.teletrack.userservice.unit.security;

import com.teletrack.userservice.entity.User;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.repository.UserRepository;
import com.teletrack.userservice.security.CustomUserDetails;
import com.teletrack.userservice.security.JwtService;
import com.teletrack.userservice.security.OAuth2LoginSuccessHandler;
import com.teletrack.userservice.security.OAuth2UserService;
import com.teletrack.userservice.service.OAuthStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
@DisplayName("OAuth2LoginSuccessHandler Unit Tests")
class OAuth2LoginSuccessHandlerTest {

    @Mock
    private OAuth2UserService oAuth2UserService;

    @Mock
    private JwtService jwtService;

    @Mock
    private OAuthStateService oAuthStateService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2User oAuth2User;

    @Mock
    private RedirectStrategy redirectStrategy;

    private OAuth2LoginSuccessHandler handler;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private User activeApprovedUser;

    @BeforeEach
    void setUp() {
        handler = new OAuth2LoginSuccessHandler(oAuth2UserService, jwtService, oAuthStateService, userRepository);
        ReflectionTestUtils.setField(handler, "frontendUrl", "http://localhost:3000");
        handler.setRedirectStrategy(redirectStrategy);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        activeApprovedUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .role(UserRole.OPERATOR)
                .active(true)
                .approved(true)
                .build();

        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(oAuth2User.getAttribute("email")).thenReturn("test@example.com");
    }

    @Test
    @DisplayName("Existing user: successful login redirects with tokens")
    void onAuthenticationSuccess_ExistingUser_RedirectsWithTokens() throws IOException {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(activeApprovedUser));
        when(oAuth2UserService.processOAuth2User(any(), anyString())).thenReturn(activeApprovedUser);
        when(jwtService.generateAccessToken(any(CustomUserDetails.class), any())).thenReturn("access-token");
        when(oAuth2UserService.createRefreshToken(any())).thenReturn("refresh-token");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(redirectStrategy).sendRedirect(eq(request), eq(response),
                contains("access-token"));
        verify(redirectStrategy).sendRedirect(eq(request), eq(response),
                contains("refresh-token"));
        verify(redirectStrategy).sendRedirect(eq(request), eq(response),
                contains("success=true"));
    }

    @Test
    @DisplayName("New user with valid state token: registers and redirects with tokens")
    void onAuthenticationSuccess_NewUserWithValidState_RegistersAndRedirects() throws IOException {
        request.setParameter("state", "google::" + "valid-state-token");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(oAuthStateService.validateAndConsumeState("valid-state-token")).thenReturn(UserRole.OPERATOR);
        when(oAuth2UserService.processOAuth2User(any(), anyString())).thenReturn(activeApprovedUser);
        when(jwtService.generateAccessToken(any(CustomUserDetails.class), any())).thenReturn("new-access-token");
        when(oAuth2UserService.createRefreshToken(any())).thenReturn("new-refresh-token");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(oAuthStateService).validateAndConsumeState("valid-state-token");
        verify(redirectStrategy).sendRedirect(eq(request), eq(response), contains("success=true"));
    }

    @Test
    @DisplayName("New user without state token: redirects with error")
    void onAuthenticationSuccess_NewUserWithoutState_RedirectsWithError() throws IOException {
        // No state parameter set
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(redirectStrategy).sendRedirect(eq(request), eq(response), contains("success=false"));
        verifyNoInteractions(oAuthStateService);
        verifyNoInteractions(oAuth2UserService);
    }

    @Test
    @DisplayName("New user with invalid state token: redirects with error")
    void onAuthenticationSuccess_NewUserWithInvalidState_RedirectsWithError() throws IOException {
        request.setParameter("state", "google::bad-token");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(oAuthStateService.validateAndConsumeState("bad-token"))
                .thenThrow(new IllegalArgumentException("Invalid state"));

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(redirectStrategy).sendRedirect(eq(request), eq(response), contains("success=false"));
        verify(oAuthStateService).validateAndConsumeState("bad-token");
    }

    @Test
    @DisplayName("Unapproved user: clears security context and redirects with error")
    void onAuthenticationSuccess_UnapprovedUser_RedirectsWithError() throws IOException {
        User unapprovedUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .role(UserRole.OPERATOR)
                .active(true)
                .approved(false)
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(unapprovedUser));
        when(oAuth2UserService.processOAuth2User(any(), anyString())).thenReturn(unapprovedUser);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(redirectStrategy).sendRedirect(eq(request), eq(response), contains("success=false"));
        verify(redirectStrategy).sendRedirect(eq(request), eq(response), contains("pending approval"));
    }

    @Test
    @DisplayName("Inactive user: clears security context and redirects with error")
    void onAuthenticationSuccess_InactiveUser_RedirectsWithError() throws IOException {
        User inactiveUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .role(UserRole.OPERATOR)
                .active(false)
                .approved(true)
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(inactiveUser));
        when(oAuth2UserService.processOAuth2User(any(), anyString())).thenReturn(inactiveUser);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(redirectStrategy).sendRedirect(eq(request), eq(response), contains("success=false"));
        verify(redirectStrategy).sendRedirect(eq(request), eq(response), contains("deactivated"));
    }

    @Test
    @DisplayName("Error processing OAuth user: redirects with error")
    void onAuthenticationSuccess_ProcessingError_RedirectsWithError() throws IOException {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(activeApprovedUser));
        when(oAuth2UserService.processOAuth2User(any(), anyString()))
                .thenThrow(new RuntimeException("DB error"));

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(redirectStrategy).sendRedirect(eq(request), eq(response), contains("success=false"));
        verify(redirectStrategy).sendRedirect(eq(request), eq(response), contains("Error creating"));
    }

    @Test
    @DisplayName("State parameter without '::' separator is treated as no state token for new user")
    void onAuthenticationSuccess_StateWithoutSeparator_TreatedAsNoState() throws IOException {
        request.setParameter("state", "noseparatortoken");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        handler.onAuthenticationSuccess(request, response, authentication);

        // stateToken is null since no "::" separator → missing state → error redirect
        verify(redirectStrategy).sendRedirect(eq(request), eq(response), contains("success=false"));
    }
}
