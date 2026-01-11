package com.teletrack.userservice.controller;

import com.teletrack.commonutils.dto.response.PageResponse;
import com.teletrack.userservice.dto.AuditLogResponse;
import com.teletrack.userservice.dto.SystemStatsResponse;
import com.teletrack.userservice.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin-only endpoints for system management")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/audit-logs")
    @Operation(summary = "Get audit logs with optional filters",
            description = "Filter by user, action, date range with pagination")
    public ResponseEntity<PageResponse<AuditLogResponse>> getAuditLogs(
            @Parameter(description = "User ID filter")
            @RequestParam(required = false) UUID user,

            @Parameter(description = "Action filter (e.g., LOGIN, CREATE_USER)")
            @RequestParam(required = false) String action,

            @Parameter(description = "Start date (ISO format: 2026-01-01T00:00:00)")
            @RequestParam(name = "date-from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,

            @Parameter(description = "End date (ISO format: 2026-01-31T23:59:59)")
            @RequestParam(name = "date-to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,

            @PageableDefault(size = 50, sort = "timestamp,desc") Pageable pageable) {

        return ResponseEntity.ok(adminService.getAuditLogs(user, action, dateFrom, dateTo, pageable));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get system statistics")
    public ResponseEntity<SystemStatsResponse> getSystemStats() {
        return ResponseEntity.ok(adminService.getSystemStats());
    }
}