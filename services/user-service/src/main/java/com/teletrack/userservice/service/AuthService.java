package com.teletrack.userservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teletrack.commonutils.dto.exception.BadRequestException;
import com.teletrack.commonutils.dto.exception.ResourceNotFoundException;
import com.teletrack.commonutils.dto.request.EmailVerificationRequest;
import com.teletrack.commonutils.dto.request.LoginRequest;
import com.teletrack.commonutils.dto.request.RefreshTokenRequest;
import com.teletrack.commonutils.dto.request.RegisterRequest;
import com.teletrack.commonutils.dto.response.ApiResponse;
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
import lombok.RequiredArgsConstructor;
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
    private final ObjectMapper objectMapper;

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

        // TODO create account created event
//        emailService.sendVerificationEmail(user.getEmail(), verificationToken);

        return ApiResponse.builder()
                .success(true)
                .message("Registration successful. Please check your email to verify your account.")
                .build();
    }

    @Transactional
    public Object login(LoginRequest request) {
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
                .message("Email verified successfully. You can now log in.")
                .build();
    }

    @Transactional
    public ApiResponse resendVerificationEmail(EmailVerificationRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getActive()) {
            throw new BadRequestException("Email already verified");
        }

        Optional<EmailVerificationToken> verif = emailVerificationTokenRepository
                .findByUserAndUsedIsFalseAndExpiresAtAfter(user, LocalDateTime.now());

        if (verif.isPresent()) {
            // TODO send verif email event
//            emailService.sendVerificationEmail(user.getEmail(), verif.get().getToken());
        } else {
            String verificationToken = UUID.randomUUID().toString();
            EmailVerificationToken emailToken = EmailVerificationToken.builder()
                    .user(user)
                    .token(verificationToken)
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .used(false)
                    .build();
            emailVerificationTokenRepository.save(emailToken);
            // TODO send verif email event
//            emailService.sendVerificationEmail(user.getEmail(), verificationToken);
        }

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
                .user(userMapper.mapToUserResponse(user))
                .build();
    }

    public UserResponse getCurrentUser() {
        User user = getCurrentAuthenticatedUser();
        return userMapper.mapToUserResponse(user);
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
                .user(userMapper.mapToUserResponse(user))
                .build();
    }
}