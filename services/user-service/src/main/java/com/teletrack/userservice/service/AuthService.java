package com.teletrack.userservice.service;

import com.teletrack.commonutils.exception.BadRequestException;
import com.teletrack.commonutils.exception.ResourceNotFoundException;
import com.teletrack.commonutils.dto.request.EmailVerificationRequest;
import com.teletrack.commonutils.dto.request.LoginRequest;
import com.teletrack.commonutils.dto.request.RefreshTokenRequest;
import com.teletrack.commonutils.dto.request.RegisterRequest;
import com.teletrack.commonutils.dto.response.ApiResponse;
import com.teletrack.commonutils.dto.response.AuthResponse;
import com.teletrack.commonutils.dto.response.UserResponse;
import com.teletrack.commonutils.event.EmailVerificationEvent;
import com.teletrack.commonutils.event.UserRegisteredEvent;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final EventPublisher eventPublisher;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional
    public ApiResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.valueOf(String.valueOf(request.getRole())))
                .active(false)
                .approved(false)
                .build();

        user = userRepository.save(user);

        String verificationToken = UUID.randomUUID().toString();
        EmailVerificationToken emailToken = EmailVerificationToken.builder()
                .user(user)
                .token(verificationToken)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();
        emailVerificationTokenRepository.save(emailToken);

        // Publish user registered event
        UserRegisteredEvent registeredEvent = UserRegisteredEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("USER_REGISTERED")
                .timestamp(LocalDateTime.now())
                .correlationId(UUID.randomUUID().toString())
                .userId(user.getId().toString())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .verificationToken(verificationToken)
                .build();

        eventPublisher.publishEvent("user.registered", registeredEvent);

        // Publish email verification event
        String verificationLink = baseUrl + "/api/v1/auth/email/confirm?token=" + verificationToken;

        EmailVerificationEvent verificationEvent = EmailVerificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("EMAIL_VERIFICATION")
                .timestamp(LocalDateTime.now())
                .correlationId(UUID.randomUUID().toString())
                .userId(user.getId().toString())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .verificationToken(verificationToken)
                .verificationLink(verificationLink)
                .build();

        eventPublisher.publishEvent("email.verification", verificationEvent);

        return ApiResponse.builder()
                .success(true)
                .message("Registration successful. Please check your email to verify your account.")
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail().toLowerCase(),
                        request.getPassword()
                )
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        if (!user.getActive()) {
            throw new BadRequestException("Please verify your email address before logging in");
        }

        if (!user.getApproved()) {
            throw new BadRequestException("Your account has not been approved. Please contact admin.");
        }

        return generateAuthResponse(user, userDetails);
    }

    @Transactional
    public ApiResponse verifyEmail(String token) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid verification token"));

        if (verificationToken.isUsed()) {
            throw new BadRequestException("Verification token has already been used");
        }

        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Verification token has expired");
        }

        User user = verificationToken.getUser();
        user.setActive(true);
        userRepository.save(user);

        verificationToken.setUsed(true);
        emailVerificationTokenRepository.save(verificationToken);

        return ApiResponse.builder()
                .success(true)
                .message("Email verified successfully. You need to wait for an admin to approve your registration before logging in.")
                .build();
    }

    @Transactional
    public ApiResponse resendVerificationEmail(EmailVerificationRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getActive()) {
            throw new BadRequestException("Email already verified");
        }

        Optional<EmailVerificationToken> existingToken = emailVerificationTokenRepository
                .findByUserAndUsedIsFalseAndExpiresAtAfter(user, LocalDateTime.now());

        String verificationToken;

        if (existingToken.isPresent()) {
            verificationToken = existingToken.get().getToken();
        } else {
            verificationToken = UUID.randomUUID().toString();
            EmailVerificationToken emailToken = EmailVerificationToken.builder()
                    .user(user)
                    .token(verificationToken)
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .used(false)
                    .build();
            emailVerificationTokenRepository.save(emailToken);
        }

        // Publish email verification event
        String verificationLink = baseUrl + "/api/v1/auth/email/confirm?token=" + verificationToken;

        EmailVerificationEvent event = EmailVerificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("EMAIL_VERIFICATION")
                .timestamp(LocalDateTime.now())
                .correlationId(UUID.randomUUID().toString())
                .userId(user.getId().toString())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .verificationToken(verificationToken)
                .verificationLink(verificationLink)
                .build();

        eventPublisher.publishEvent("email.verification", event);

        return ApiResponse.builder()
                .success(true)
                .message("Verification email sent. Please check your inbox.")
                .build();
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (refreshToken.isRevoked()) {
            throw new BadRequestException("Refresh token has been revoked");
        }

        if (refreshToken.isUsed()) {
            throw new BadRequestException("Refresh token has already been used");
        }

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Refresh token has expired");
        }

        User user = refreshToken.getUser();

        if (!user.getActive() || !user.getApproved()) {
            throw new BadRequestException("User account is not active");
        }

        refreshToken.setUsed(true);
        refreshTokenRepository.save(refreshToken);

        CustomUserDetails userDetails = new CustomUserDetails(user);
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("role", user.getRole().toString());

        String newAccessToken = jwtService.generateAccessToken(userDetails, claims);
        String newRefreshToken = UUID.randomUUID().toString();

        RefreshToken newRefreshTokenEntity = RefreshToken.builder()
                .user(user)
                .token(newRefreshToken)
                .expiresAt(LocalDateTime.now().plus(jwtService.getRefreshTokenExpiration(), ChronoUnit.MILLIS))
                .used(false)
                .revoked(false)
                .build();
        refreshTokenRepository.save(newRefreshTokenEntity);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .user(userMapper.toUserResponse(user))
                .build();
    }

    public UserResponse getCurrentUser() {
        User user = getCurrentAuthenticatedUser();
        return userMapper.toUserResponse(user);
    }

    // Helper methods
    private User getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadRequestException("User not authenticated");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getUser();
    }

    private AuthResponse generateAuthResponse(User user, CustomUserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("role", user.getRole().toString());

        String accessToken = jwtService.generateAccessToken(userDetails, claims);
        String refreshToken = UUID.randomUUID().toString();

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plus(jwtService.getRefreshTokenExpiration(), ChronoUnit.MILLIS))
                .used(false)
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(userMapper.toUserResponse(user))
                .build();
    }
}