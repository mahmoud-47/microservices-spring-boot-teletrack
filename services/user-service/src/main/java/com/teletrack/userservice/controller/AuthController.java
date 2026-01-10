package com.teletrack.userservice.controller;

import com.teletrack.commonutils.dto.request.EmailVerificationRequest;
import com.teletrack.commonutils.dto.request.LoginRequest;
import com.teletrack.commonutils.dto.request.RefreshTokenRequest;
import com.teletrack.commonutils.dto.request.RegisterRequest;
import com.teletrack.commonutils.dto.response.ApiResponse;
import com.teletrack.commonutils.dto.response.AuthResponse;
import com.teletrack.commonutils.dto.response.UserResponse;
import com.teletrack.userservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user registration, login, oauth : http://localhost:8080/oauth2/authorization/google?state=role:OPERATOR")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Login a user",
            description = "Login with email and password."
    )
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Object response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/email/confirm")
    @Operation(summary = "Confirm user email via token")
    public ResponseEntity<ApiResponse> confirmEmail(
            @Parameter(description = "Email confirmation token") @RequestParam String token) {
        return ResponseEntity.ok(authService.verifyEmail(token));
    }

    @PostMapping("/email/verify")
    @Operation(summary = "Resend verification email")
    public ResponseEntity<ApiResponse> resendVerificationEmail(
            @Valid @RequestBody EmailVerificationRequest request) {
        return ResponseEntity.ok(authService.resendVerificationEmail(request));
    }


    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT access token")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @GetMapping("/me")
    @Operation(
            summary = "Get the current authenticated user",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<UserResponse> getCurrentUser() {
        return ResponseEntity.ok(authService.getCurrentUser());
    }
}