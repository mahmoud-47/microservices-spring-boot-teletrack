package com.teletrack.userservice.controller;

import com.teletrack.commonutils.dto.request.UpdateUserRequest;
import com.teletrack.commonutils.dto.response.ApiResponse;
import com.teletrack.commonutils.dto.response.PageResponse;
import com.teletrack.commonutils.dto.response.UserResponse;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Endpoints for managing users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'SUPPORT')")
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "User ID") @PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping("/list")
    @Operation(summary = "Get all users with pagination and optional role filter")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<UserResponse>> getAllUsers(
            @Parameter(description = "Filter by role") @RequestParam(required = false) UserRole role,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(role, pageable));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user details")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'SUPPORT')")
    public ResponseEntity<UserResponse> updateUser(
            @Parameter(description = "User ID") @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Approve user account")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> approveUser(
            @Parameter(description = "User ID") @PathVariable UUID id) {
        return ResponseEntity.ok(userService.approveUser(id));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate user account")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> deactivateUser(
            @Parameter(description = "User ID") @PathVariable UUID id) {
        return ResponseEntity.ok(userService.deactivateUser(id));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate user account")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> activateUser(
            @Parameter(description = "User ID") @PathVariable UUID id) {
        return ResponseEntity.ok(userService.activateUser(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user permanently")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> deleteUser(
            @Parameter(description = "User ID") @PathVariable UUID id) {
        return ResponseEntity.ok(userService.deleteUser(id));
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Change user role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> changeUserRole(
            @Parameter(description = "User ID") @PathVariable UUID id,
            @Parameter(description = "New role") @RequestParam UserRole role) {
        return ResponseEntity.ok(userService.changeUserRole(id, role));
    }

    @GetMapping("/validate/{id}")
    @Operation(summary = "Validate if user exists and is active (for service-to-service calls)")
    public ResponseEntity<Boolean> validateUser(
            @Parameter(description = "User ID") @PathVariable UUID id) {
        return ResponseEntity.ok(userService.validateUser(id));
    }
}