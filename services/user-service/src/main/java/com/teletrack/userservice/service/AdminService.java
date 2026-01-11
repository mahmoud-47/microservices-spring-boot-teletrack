package com.teletrack.userservice.service;

import com.teletrack.commonutils.dto.response.PageResponse;
import com.teletrack.userservice.dto.AuditLogResponse;
import com.teletrack.userservice.dto.SystemStatsResponse;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.mapper.AuditLogMapper;
import com.teletrack.userservice.repository.AuditLogRepository;
import com.teletrack.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AuditLogMapper auditLogMapper;

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogs(
            UUID userId,
            String action,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            Pageable pageable) {

        Page<AuditLogResponse> page = auditLogRepository
                .findWithFilters(userId, action, dateFrom, dateTo, pageable)
                .map(auditLogMapper::toAuditLogResponse);

        return PageResponse.<AuditLogResponse>builder()
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

    @Transactional(readOnly = true)
    public SystemStatsResponse getSystemStats() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByActiveAndApproved(true, true);
        long pendingApproval = userRepository.countByActiveAndApproved(false, false);

        Map<String, Long> usersByRole = new HashMap<>();
        usersByRole.put("ADMIN", userRepository.countByRole(UserRole.ADMIN));
        usersByRole.put("OPERATOR", userRepository.countByRole(UserRole.OPERATOR));
        usersByRole.put("SUPPORT", userRepository.countByRole(UserRole.SUPPORT));

        return SystemStatsResponse.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .pendingApproval(pendingApproval)
                .usersByRole(usersByRole)
                .lastUpdated(LocalDateTime.now())
                .build();
    }
}