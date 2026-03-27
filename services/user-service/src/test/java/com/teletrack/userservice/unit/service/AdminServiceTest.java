package com.teletrack.userservice.unit.service;

import com.teletrack.commonutils.dto.response.PageResponse;
import com.teletrack.userservice.dto.AuditLogResponse;
import com.teletrack.userservice.dto.SystemStatsResponse;
import com.teletrack.userservice.entity.AuditLog;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.mapper.AuditLogMapper;
import com.teletrack.userservice.repository.AuditLogRepository;
import com.teletrack.userservice.repository.UserRepository;
import com.teletrack.userservice.service.AdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
@DisplayName("AdminService Unit Tests")
class AdminServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogMapper auditLogMapper;

    @InjectMocks
    private AdminService adminService;

    // ─── getAuditLogs ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return paginated audit logs with no filters")
    void getAuditLogs_NoFilters_ReturnsPage() {
        Pageable pageable = PageRequest.of(0, 20);
        AuditLog log1 = AuditLog.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).action("LOGIN").build();
        Page<AuditLog> page = new PageImpl<>(List.of(log1), pageable, 1);
        AuditLogResponse response = AuditLogResponse.builder()
                .id(log1.getId().toString()).action("LOGIN").build();

        when(auditLogRepository.findWithFilters(null, null, null, null, pageable)).thenReturn(page);
        when(auditLogMapper.toAuditLogResponse(log1)).thenReturn(response);

        PageResponse<AuditLogResponse> result = adminService.getAuditLogs(null, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getPage()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("Should pass userId filter to repository")
    void getAuditLogs_WithUserIdFilter() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        when(auditLogRepository.findWithFilters(eq(userId), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(Page.empty());

        adminService.getAuditLogs(userId, null, null, null, pageable);

        verify(auditLogRepository).findWithFilters(userId, null, null, null, pageable);
    }

    @Test
    @DisplayName("Should pass action filter to repository")
    void getAuditLogs_WithActionFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        when(auditLogRepository.findWithFilters(isNull(), eq("APPROVE_USER"), isNull(), isNull(), eq(pageable)))
                .thenReturn(Page.empty());

        adminService.getAuditLogs(null, "APPROVE_USER", null, null, pageable);

        verify(auditLogRepository).findWithFilters(null, "APPROVE_USER", null, null, pageable);
    }

    @Test
    @DisplayName("Should pass date range filter to repository")
    void getAuditLogs_WithDateRangeFilter() {
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        LocalDateTime to = LocalDateTime.now();
        Pageable pageable = PageRequest.of(0, 10);
        when(auditLogRepository.findWithFilters(isNull(), isNull(), eq(from), eq(to), eq(pageable)))
                .thenReturn(Page.empty());

        adminService.getAuditLogs(null, null, from, to, pageable);

        verify(auditLogRepository).findWithFilters(null, null, from, to, pageable);
    }

    @Test
    @DisplayName("Should return empty page when no audit logs match")
    void getAuditLogs_EmptyResult() {
        Pageable pageable = PageRequest.of(0, 20);
        when(auditLogRepository.findWithFilters(any(), any(), any(), any(), eq(pageable)))
                .thenReturn(Page.empty());

        PageResponse<AuditLogResponse> result = adminService.getAuditLogs(null, null, null, null, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.isEmpty()).isTrue();
    }

    // ─── getSystemStats ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return correct system statistics")
    void getSystemStats_ReturnsCorrectCounts() {
        when(userRepository.count()).thenReturn(100L);
        when(userRepository.countByActiveAndApproved(true, true)).thenReturn(70L);
        when(userRepository.countByActiveAndApproved(false, false)).thenReturn(10L);
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(5L);
        when(userRepository.countByRole(UserRole.OPERATOR)).thenReturn(50L);
        when(userRepository.countByRole(UserRole.SUPPORT)).thenReturn(45L);

        SystemStatsResponse stats = adminService.getSystemStats();

        assertThat(stats.getTotalUsers()).isEqualTo(100L);
        assertThat(stats.getActiveUsers()).isEqualTo(70L);
        assertThat(stats.getPendingApproval()).isEqualTo(10L);
        assertThat(stats.getUsersByRole()).containsEntry("ADMIN", 5L);
        assertThat(stats.getUsersByRole()).containsEntry("OPERATOR", 50L);
        assertThat(stats.getUsersByRole()).containsEntry("SUPPORT", 45L);
        assertThat(stats.getLastUpdated()).isNotNull();
        assertThat(stats.getLastUpdated())
                .isCloseTo(LocalDateTime.now(), within(3, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("Should include lastUpdated timestamp in stats")
    void getSystemStats_IncludesLastUpdated() {
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.countByActiveAndApproved(anyBoolean(), anyBoolean())).thenReturn(0L);
        when(userRepository.countByRole(any())).thenReturn(0L);

        SystemStatsResponse stats = adminService.getSystemStats();

        assertThat(stats.getLastUpdated()).isNotNull();
    }
}
