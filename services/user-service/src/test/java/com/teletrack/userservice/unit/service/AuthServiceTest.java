package com.teletrack.userservice.unit.service;

import com.teletrack.commonutils.dto.request.EmailVerificationRequest;
import com.teletrack.commonutils.dto.request.RefreshTokenRequest;
import com.teletrack.commonutils.dto.response.ApiResponse;
import com.teletrack.commonutils.exception.BadRequestException;
import com.teletrack.commonutils.exception.ResourceNotFoundException;
import com.teletrack.commonutils.dto.request.LoginRequest;
import com.teletrack.commonutils.dto.request.RegisterRequest;
import com.teletrack.commonutils.dto.response.AuthResponse;
import com.teletrack.commonutils.dto.response.UserResponse;
import com.teletrack.userservice.entity.EmailVerificationToken;
import com.teletrack.userservice.entity.RefreshToken;
import com.teletrack.userservice.entity.User;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.mapper.UserMapper;
import com.teletrack.userservice.repository.EmailVerificationTokenRepository;
import com.teletrack.userservice.repository.RefreshTokenRepository;
import com.teletrack.userservice.repository.UserRepository;
import com.teletrack.userservice.security.CustomUserDetails;
import com.teletrack.userservice.security.JwtService;
import com.teletrack.userservice.service.AuthService;
import com.teletrack.userservice.service.EventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserMapper userMapper;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private User user;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        // Set baseUrl using ReflectionTestUtils to avoid NullPointerException
        ReflectionTestUtils.setField(authService, "baseUrl", "http://localhost:8080");

        registerRequest = RegisterRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("password123")
                .role(RegisterRequest.Role.OPERATOR)
                .build();

        user = User.builder()
                .id(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .passwordHash("encoded_password")
                .role(UserRole.OPERATOR)
                .active(true)
                .approved(false)
                .createdAt(LocalDateTime.now())
                .build();

        loginRequest = LoginRequest.builder()
                .email("john@example.com")
                .password("password123")
                .build();
    }

    @Test
    @DisplayName("Should register new user successfully")
    void shouldRegisterNewUser() {
        // Given
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(emailVerificationTokenRepository.save(any(EmailVerificationToken.class)))
                .thenReturn(new EmailVerificationToken());

        // When
        ApiResponse response = authService.register(registerRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("Registration successful");

        verify(userRepository).existsByEmail("john@example.com");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
        verify(eventPublisher, times(2)).publishEvent(anyString(), any());
    }

    @Test
    @DisplayName("Should throw exception when email already exists")
    void shouldThrowExceptionWhenEmailExists() {
        // Given
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email already registered");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void shouldLoginSuccessfully() {
        // Given
        // BUG FIX 1: User must be both active AND approved
        user.setApproved(true);
        user.setActive(true);

        // BUG FIX 2: Create actual CustomUserDetails object, not just mock Authentication
        Authentication authentication = mock(Authentication.class);
        CustomUserDetails userDetails = new CustomUserDetails(user);

        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access_token");
        when(jwtService.getRefreshTokenExpiration()).thenReturn(604800000L); // 7 days
        when(userMapper.toUserResponse(any())).thenReturn(null); // We don't care about structure
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0)); // Return same object

        // When
        AuthResponse response = authService.login(loginRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access_token");
        assertThat(response.getRefreshToken()).isNotNull();
        assertThat(response.getTokenType()).isEqualTo("Bearer");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtService).generateAccessToken(any(), any());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Should throw exception when user not approved")
    void shouldThrowExceptionWhenUserNotApproved() {
        // Given
        user.setApproved(false);
        user.setActive(true);

        // BUG FIX 2: Create actual CustomUserDetails object
        Authentication authentication = mock(Authentication.class);
        CustomUserDetails userDetails = new CustomUserDetails(user);

        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);

        // When/Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not been approved");

        verify(jwtService, never()).generateAccessToken(any(), any());
    }

    @Test
    @DisplayName("Should throw exception with invalid credentials")
    void shouldThrowExceptionWithInvalidCredentials() {
        // Given
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        // When/Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("Should verify email successfully")
    void shouldVerifyEmailSuccessfully() {
        // Given
        String token = "valid_token";
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .id(UUID.randomUUID())
                .token(token)
                .user(user)
                .used(false)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(emailVerificationTokenRepository.findByToken(token))
                .thenReturn(Optional.of(verificationToken));
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        ApiResponse response = authService.verifyEmail(token);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("verified");

        ArgumentCaptor<EmailVerificationToken> tokenCaptor =
                ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(emailVerificationTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().isUsed()).isTrue();
    }

    @Test
    @DisplayName("Should throw exception for expired token")
    void shouldThrowExceptionForExpiredToken() {
        // Given
        String token = "expired_token";
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .token(token)
                .user(user)
                .used(false)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(emailVerificationTokenRepository.findByToken(token))
                .thenReturn(Optional.of(verificationToken));

        // When/Then
        assertThatThrownBy(() -> authService.verifyEmail(token))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should refresh token successfully")
    void shouldRefreshTokenSuccessfully() {
        // Given
        // BUG FIX 3: User must be BOTH active AND approved for refresh to work
        user.setActive(true);
        user.setApproved(true);

        String oldRefreshToken = "old_refresh_token";
        RefreshToken refreshToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .token(oldRefreshToken)
                .user(user)
                .used(false)
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(oldRefreshToken)
                .build();

        when(refreshTokenRepository.findByToken(oldRefreshToken))
                .thenReturn(Optional.of(refreshToken));
        when(jwtService.generateAccessToken(any(), any())).thenReturn("new_access_token");
        when(jwtService.getRefreshTokenExpiration()).thenReturn(604800000L);
        when(userMapper.toUserResponse(any())).thenReturn(null);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AuthResponse response = authService.refreshToken(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new_access_token");
        assertThat(response.getRefreshToken()).isNotNull();
        assertThat(response.getTokenType()).isEqualTo("Bearer");

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(tokenCaptor.capture());

        // First save: mark old token as used
        assertThat(tokenCaptor.getAllValues().get(0).isUsed()).isTrue();

        // Second save: create new token
        assertThat(tokenCaptor.getAllValues().get(1).isUsed()).isFalse();
        assertThat(tokenCaptor.getAllValues().get(1).isRevoked()).isFalse();
    }

    @Test
    @DisplayName("Should throw exception for already used refresh token")
    void shouldThrowExceptionForUsedRefreshToken() {
        // Given
        String usedToken = "used_token";
        RefreshToken refreshToken = RefreshToken.builder()
                .token(usedToken)
                .user(user)
                .used(true)
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(usedToken)
                .build();

        when(refreshTokenRepository.findByToken(usedToken))
                .thenReturn(Optional.of(refreshToken));

        // When/Then
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been used");

        verify(jwtService, never()).generateAccessToken(any(), any());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── verifyEmail edge cases ───────────────────────────────────────────────

    @Test
    @DisplayName("Should throw exception when verification token is not found")
    void shouldThrowExceptionForInvalidVerificationToken() {
        when(emailVerificationTokenRepository.findByToken("bad_token"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("bad_token"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid verification token");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception when verification token is already used")
    void shouldThrowExceptionForAlreadyUsedVerificationToken() {
        String token = "used_verify_token";
        EmailVerificationToken usedToken = EmailVerificationToken.builder()
                .token(token)
                .user(user)
                .used(true)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(emailVerificationTokenRepository.findByToken(token))
                .thenReturn(Optional.of(usedToken));

        assertThatThrownBy(() -> authService.verifyEmail(token))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been used");

        verify(userRepository, never()).save(any(User.class));
    }

    // ─── login edge cases ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Should throw exception when user is active but not yet approved")
    void shouldThrowExceptionWhenUserInactiveEmailNotVerified() {
        user.setActive(false);
        user.setApproved(false);

        Authentication authentication = mock(Authentication.class);
        CustomUserDetails userDetails = new CustomUserDetails(user);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("verify your email");

        verify(jwtService, never()).generateAccessToken(any(), any());
    }

    // ─── resendVerificationEmail ──────────────────────────────────────────────

    @Test
    @DisplayName("Should resend verification email when no active token exists")
    void shouldResendVerificationEmailWhenNoActiveTokenExists() {
        user.setActive(false);
        EmailVerificationRequest request = EmailVerificationRequest.builder()
                .email("john@example.com")
                .build();

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(emailVerificationTokenRepository
                .findByUserAndUsedIsFalseAndExpiresAtAfter(eq(user), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(emailVerificationTokenRepository.save(any(EmailVerificationToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApiResponse response = authService.resendVerificationEmail(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("Verification email sent");
        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(eventPublisher).publishEvent(eq("email.verification"), any());
    }

    @Test
    @DisplayName("Should reuse existing active token when resending verification email")
    void shouldReuseExistingActiveTokenWhenResending() {
        user.setActive(false);
        EmailVerificationRequest request = EmailVerificationRequest.builder()
                .email("john@example.com")
                .build();

        EmailVerificationToken existingToken = EmailVerificationToken.builder()
                .token("existing_token")
                .user(user)
                .used(false)
                .expiresAt(LocalDateTime.now().plusHours(20))
                .build();

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(emailVerificationTokenRepository
                .findByUserAndUsedIsFalseAndExpiresAtAfter(eq(user), any(LocalDateTime.class)))
                .thenReturn(Optional.of(existingToken));

        ApiResponse response = authService.resendVerificationEmail(request);

        assertThat(response.isSuccess()).isTrue();
        verify(emailVerificationTokenRepository, never()).save(any(EmailVerificationToken.class));
        verify(eventPublisher).publishEvent(eq("email.verification"), any());
    }

    @Test
    @DisplayName("Should throw exception when resending to already verified user")
    void shouldThrowExceptionWhenResendingToActiveUser() {
        user.setActive(true);
        EmailVerificationRequest request = EmailVerificationRequest.builder()
                .email("john@example.com")
                .build();

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.resendVerificationEmail(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email already verified");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when resending to unknown email")
    void shouldThrowExceptionWhenResendingToUnknownEmail() {
        EmailVerificationRequest request = EmailVerificationRequest.builder()
                .email("nobody@example.com")
                .build();

        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resendVerificationEmail(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // ─── refreshToken edge cases ──────────────────────────────────────────────

    @Test
    @DisplayName("Should throw exception for revoked refresh token")
    void shouldThrowExceptionForRevokedRefreshToken() {
        String revokedToken = "revoked_token";
        RefreshToken refreshToken = RefreshToken.builder()
                .token(revokedToken)
                .user(user)
                .used(false)
                .revoked(true)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(refreshTokenRepository.findByToken(revokedToken)).thenReturn(Optional.of(refreshToken));

        assertThatThrownBy(() -> authService.refreshToken(
                RefreshTokenRequest.builder().refreshToken(revokedToken).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("revoked");

        verify(jwtService, never()).generateAccessToken(any(), any());
    }

    @Test
    @DisplayName("Should throw exception for expired refresh token")
    void shouldThrowExceptionForExpiredRefreshToken() {
        String expiredToken = "expired_refresh";
        RefreshToken refreshToken = RefreshToken.builder()
                .token(expiredToken)
                .user(user)
                .used(false)
                .revoked(false)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(refreshTokenRepository.findByToken(expiredToken)).thenReturn(Optional.of(refreshToken));

        assertThatThrownBy(() -> authService.refreshToken(
                RefreshTokenRequest.builder().refreshToken(expiredToken).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");

        verify(jwtService, never()).generateAccessToken(any(), any());
    }

    @Test
    @DisplayName("Should throw exception for refresh token when user is not active")
    void shouldThrowExceptionForRefreshTokenWhenUserInactive() {
        user.setActive(false);
        user.setApproved(true);

        String token = "valid_format_token";
        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .user(user)
                .used(false)
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(refreshTokenRepository.findByToken(token)).thenReturn(Optional.of(refreshToken));

        assertThatThrownBy(() -> authService.refreshToken(
                RefreshTokenRequest.builder().refreshToken(token).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not active");

        verify(jwtService, never()).generateAccessToken(any(), any());
    }

    @Test
    @DisplayName("Should throw exception when refresh token is not found")
    void shouldThrowExceptionForInvalidRefreshToken() {
        when(refreshTokenRepository.findByToken("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken(
                RefreshTokenRequest.builder().refreshToken("nonexistent").build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    // ─── getCurrentUser ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return current user when authenticated")
    void shouldGetCurrentUserWhenAuthenticated() {
        user.setActive(true);
        user.setApproved(true);

        Authentication authentication = mock(Authentication.class);
        CustomUserDetails userDetails = new CustomUserDetails(user);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        org.springframework.security.core.context.SecurityContext ctx =
                mock(org.springframework.security.core.context.SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(ctx);

        UserResponse stubResponse = UserResponse.builder()
                .id(user.getId().toString())
                .email(user.getEmail())
                .role("OPERATOR")
                .build();
        when(userMapper.toUserResponse(user)).thenReturn(stubResponse);

        UserResponse result = authService.getCurrentUser();

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("Should throw exception when no authentication in security context")
    void shouldThrowWhenGetCurrentUserWithNoAuthentication() {
        org.springframework.security.core.context.SecurityContext ctx =
                mock(org.springframework.security.core.context.SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(ctx);

        assertThatThrownBy(() -> authService.getCurrentUser())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not authenticated");
    }
}