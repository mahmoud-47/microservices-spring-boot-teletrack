package com.teletrack.incidentservice.controller;

import com.teletrack.commonutils.dto.request.AssignIncidentRequest;
import com.teletrack.commonutils.dto.request.CreateIncidentRequest;
import com.teletrack.commonutils.dto.request.UpdateIncidentRequest;
import com.teletrack.commonutils.dto.response.ApiResponse;
import com.teletrack.commonutils.dto.response.IncidentResponse;
import com.teletrack.commonutils.dto.response.PageResponse;
import com.teletrack.commonutils.enums.IncidentStatus;
import com.teletrack.incidentservice.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/incidents")
@RequiredArgsConstructor
@Tag(name = "Incident Management", description = "APIs for managing telecom incidents throughout their lifecycle")
public class IncidentController {

    private final IncidentService incidentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPPORT', 'ADMIN')")
    @Operation(
            summary = "Create a new incident",
            description = "Create a new incident report. Available to OPERATOR, SUPPORT, and ADMIN roles.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse> createIncident(
            @Valid @RequestBody CreateIncidentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = extractUserId(userDetails);
        IncidentResponse incident = incidentService.createIncident(request, userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.builder()
                        .success(true)
                        .message("Incident created successfully")
                        .data(incident)
                        .build());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Get incident by ID",
            description = "Retrieve detailed information about a specific incident including its history",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse> getIncidentById(@PathVariable UUID id) {
        IncidentResponse incident = incidentService.getIncidentById(id);

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Incident retrieved successfully")
                .data(incident)
                .build());
    }

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
    @Operation(
            summary = "Get all incidents",
            description = "Retrieve paginated list of all incidents. Available to SUPPORT and ADMIN roles only.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse> getAllIncidents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("ASC") ?
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<IncidentResponse> incidents = incidentService.getAllIncidents(pageable);

        PageResponse<IncidentResponse> pageResponse = PageResponse.<IncidentResponse>builder()
                .content(incidents.getContent())
                .page(incidents.getNumber())
                .size(incidents.getSize())
                .totalElements(incidents.getTotalElements())
                .totalPages(incidents.getTotalPages())
                .last(incidents.isLast())
                .first(incidents.isFirst())
                .empty(incidents.isEmpty())
                .build();

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Incidents retrieved successfully")
                .data(pageResponse)
                .build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPPORT', 'ADMIN')")
    @Operation(
            summary = "Update incident",
            description = "Update incident details such as title, description, priority, or status",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse> updateIncident(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIncidentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = extractUserId(userDetails);
        IncidentResponse incident = incidentService.updateIncident(id, request, userId);

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Incident updated successfully")
                .data(incident)
                .build());
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
    @Operation(
            summary = "Assign incident to user",
            description = "Assign an incident to a support user for resolution. Available to SUPPORT and ADMIN roles.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse> assignIncident(
            @PathVariable UUID id,
            @Valid @RequestBody AssignIncidentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = extractUserId(userDetails);
        IncidentResponse incident = incidentService.assignIncident(id, request, userId);

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Incident assigned successfully")
                .data(incident)
                .build());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
    @Operation(
            summary = "Change incident status",
            description = "Update incident status (OPEN, IN_PROGRESS, RESOLVED, CLOSED). Available to SUPPORT and ADMIN roles.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse> changeStatus(
            @PathVariable UUID id,
            @RequestParam IncidentStatus status,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = extractUserId(userDetails);
        IncidentResponse incident = incidentService.changeStatus(id, status, userId);

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Incident status changed successfully")
                .data(incident)
                .build());
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Get my incidents",
            description = "Retrieve all incidents reported by the current user",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse> getMyIncidents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = extractUserId(userDetails);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<IncidentResponse> incidents = incidentService.getIncidentsByReportedBy(userId, pageable);

        PageResponse<IncidentResponse> pageResponse = PageResponse.<IncidentResponse>builder()
                .content(incidents.getContent())
                .page(incidents.getNumber())
                .size(incidents.getSize())
                .totalElements(incidents.getTotalElements())
                .totalPages(incidents.getTotalPages())
                .last(incidents.isLast())
                .first(incidents.isFirst())
                .empty(incidents.isEmpty())
                .build();

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("My incidents retrieved successfully")
                .data(pageResponse)
                .build());
    }

    @GetMapping("/assigned")
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
    @Operation(
            summary = "Get assigned incidents",
            description = "Retrieve all incidents assigned to the current user. Available to SUPPORT and ADMIN roles.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse> getAssignedIncidents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = extractUserId(userDetails);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<IncidentResponse> incidents = incidentService.getIncidentsByAssignedTo(userId, pageable);

        PageResponse<IncidentResponse> pageResponse = PageResponse.<IncidentResponse>builder()
                .content(incidents.getContent())
                .page(incidents.getNumber())
                .size(incidents.getSize())
                .totalElements(incidents.getTotalElements())
                .totalPages(incidents.getTotalPages())
                .last(incidents.isLast())
                .first(incidents.isFirst())
                .empty(incidents.isEmpty())
                .build();

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Assigned incidents retrieved successfully")
                .data(pageResponse)
                .build());
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
    @Operation(
            summary = "Get incidents by status",
            description = "Filter incidents by status (OPEN, IN_PROGRESS, RESOLVED, CLOSED). Available to SUPPORT and ADMIN roles.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse> getIncidentsByStatus(
            @PathVariable IncidentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<IncidentResponse> incidents = incidentService.getIncidentsByStatus(status, pageable);

        PageResponse<IncidentResponse> pageResponse = PageResponse.<IncidentResponse>builder()
                .content(incidents.getContent())
                .page(incidents.getNumber())
                .size(incidents.getSize())
                .totalElements(incidents.getTotalElements())
                .totalPages(incidents.getTotalPages())
                .last(incidents.isLast())
                .first(incidents.isFirst())
                .empty(incidents.isEmpty())
                .build();

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Incidents retrieved successfully")
                .data(pageResponse)
                .build());
    }

    private UUID extractUserId(UserDetails userDetails) {
        // Assuming the username is the userId (UUID as string)
        return UUID.fromString(userDetails.getUsername());
    }
}