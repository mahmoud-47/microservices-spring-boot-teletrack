package com.teletrack.userservice.unit.service;

import com.teletrack.commonutils.dto.request.RefreshTokenRequest;
import com.teletrack.commonutils.dto.response.ApiResponse;
import com.teletrack.commonutils.exception.BadRequestException;
import com.teletrack.commonutils.dto.request.LoginRequest;
import com.teletrack.commonutils.dto.request.RegisterRequest;
import com.teletrack.commonutils.dto.response.AuthResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
}