package com.teletrack.userservice.service;

import com.teletrack.commonutils.exception.BadRequestException;
import com.teletrack.commonutils.exception.ResourceNotFoundException;
import com.teletrack.commonutils.dto.request.UpdateUserRequest;
import com.teletrack.commonutils.dto.response.ApiResponse;
import com.teletrack.commonutils.dto.response.PageResponse;
import com.teletrack.commonutils.dto.response.UserResponse;
import com.teletrack.commonutils.event.UserApprovedEvent;
import com.teletrack.commonutils.event.UserDeactivatedEvent;
import com.teletrack.userservice.entity.AuditLog;
import com.teletrack.userservice.entity.User;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.mapper.UserMapper;
import com.teletrack.userservice.repository.AuditLogRepository;
import com.teletrack.userservice.repository.UserRepository;
import com.teletrack.userservice.security.CustomUserDetails;
import com.teletrack.userservice.util.IpAddressUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserMapper userMapper;
    private final EventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        logAudit(getCurrentUserId(), "VIEW_USER", "Viewed user: " + id);
        return userMapper.toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getAllUsers(UserRole role, Pageable pageable) {
        Page<UserResponse> page;

        if (role != null) {
            page = userRepository.findByRole(role, pageable)
                    .map(userMapper::toUserResponse);
        } else {
            page = userRepository.findAll(pageable)
                    .map(userMapper::toUserResponse);
        }

        logAudit(getCurrentUserId(), "LIST_USERS", "Listed users with role filter: " + role);

        return PageResponse.<UserResponse>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        User currentUser = getCurrentUser();

        // Only ADMIN can update other users, others can only update themselves
        if (!currentUser.getRole().equals(UserRole.ADMIN) && !currentUser.getId().equals(id)) {
            throw new BadRequestException("You can only update your own profile");
        }

        boolean updated = false;
        StringBuilder changes = new StringBuilder("Updated fields: ");

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
            changes.append("firstName, ");
            updated = true;
        }

        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
            changes.append("lastName, ");
            updated = true;
        }

        if (!updated) {
            throw new BadRequestException("No valid fields provided for update");
        }

        user = userRepository.save(user);
        logAudit(getCurrentUserId(), "UPDATE_USER", changes.toString() + "for user: " + id);

        return userMapper.toUserResponse(user);
    }

    @Transactional
    public ApiResponse approveUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        if (!user.getActive()) {
            throw new BadRequestException("User did not confirm their email");
        }

        if (user.getApproved()) {
            throw new BadRequestException("User is already approved");
        }

        user.setApproved(true);
        user = userRepository.save(user);

        logAudit(getCurrentUserId(), "APPROVE_USER", "Approved user: " + id);

        // Publish user approved event
        UserApprovedEvent event = UserApprovedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("USER_APPROVED")
                .timestamp(LocalDateTime.now())
                .correlationId(UUID.randomUUID().toString())
                .userId(user.getId().toString())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .approvedBy(getCurrentUserId().toString())
                .build();

        eventPublisher.publishEvent("user.approved", event);

        return ApiResponse.builder()
                .success(true)
                .message("User approved successfully")
                .data(userMapper.toUserResponse(user))
                .build();
    }

    @Transactional
    public ApiResponse deactivateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        if (!user.getActive()) {
            throw new BadRequestException("User is already deactivated");
        }

        user.setActive(false);
        user = userRepository.save(user);

        logAudit(getCurrentUserId(), "DEACTIVATE_USER", "Deactivated user: " + id);

        // Publish user deactivated event
        UserDeactivatedEvent event = UserDeactivatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("USER_DEACTIVATED")
                .timestamp(LocalDateTime.now())
                .correlationId(UUID.randomUUID().toString())
                .userId(user.getId().toString())
                .email(user.getEmail())
                .deactivatedBy(getCurrentUserId().toString())
                .reason("Admin action")
                .build();

        eventPublisher.publishEvent("user.deactivated", event);

        return ApiResponse.builder()
                .success(true)
                .message("User deactivated successfully")
                .build();
    }

    @Transactional
    public ApiResponse activateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        if (user.getActive()) {
            throw new BadRequestException("User is already active");
        }

        user.setActive(true);
        user = userRepository.save(user);

        logAudit(getCurrentUserId(), "ACTIVATE_USER", "Activated user: " + id);

        return ApiResponse.builder()
                .success(true)
                .message("User activated successfully")
                .build();
    }

    @Transactional
    public ApiResponse deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        UUID currentUserId = getCurrentUserId();
        if (user.getId().equals(currentUserId)) {
            throw new BadRequestException("Cannot delete your own account");
        }

        logAudit(currentUserId, "DELETE_USER", "Deleted user: " + id + " (email: " + user.getEmail() + ")");

        userRepository.delete(user);

        return ApiResponse.builder()
                .success(true)
                .message("User deleted successfully")
                .build();
    }

    @Transactional
    public UserResponse changeUserRole(UUID id, UserRole newRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        if (user.getRole() == newRole) {
            throw new BadRequestException("User already has role: " + newRole);
        }

        UserRole oldRole = user.getRole();
        user.setRole(newRole);
        user = userRepository.save(user);

        logAudit(getCurrentUserId(), "CHANGE_USER_ROLE",
                "Changed role from " + oldRole + " to " + newRole + " for user: " + id);

        return userMapper.toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public Boolean validateUser(UUID id) {
        return userRepository.findById(id)
                .map(user -> user.getActive() && user.getApproved())
                .orElse(false);
    }

    // Helper methods
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadRequestException("User not authenticated");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails) {
            return ((CustomUserDetails) principal).getUser();
        }

        throw new BadRequestException("Invalid authentication");
    }

    private UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SERVICE"))) {
            return null; // Service call, no user ID
        }
        return getCurrentUser().getId();
    }

    private void logAudit(UUID userId, String action, String details) {
        if (userId == null) {
            return;
        }

        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .action(action)
                    .details(details)
                    .ipAddress(IpAddressUtil.getClientIpAddress())
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log audit: {}", e.getMessage());
        }
    }
}