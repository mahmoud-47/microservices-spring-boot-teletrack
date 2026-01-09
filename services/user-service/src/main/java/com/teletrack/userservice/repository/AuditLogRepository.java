package com.teletrack.userservice.repository;

import com.teletrack.userservice.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByUserId(UUID userId);

    Page<AuditLog> findByUserId(UUID userId, Pageable pageable);

    List<AuditLog> findByAction(String action);

    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND a.action = :action")
    List<AuditLog> findByUserIdAndAction(@Param("userId") UUID userId, @Param("action") String action);

    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND a.timestamp BETWEEN :startDate AND :endDate")
    List<AuditLog> findByUserIdAndTimestampBetween(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :startDate AND :endDate ORDER BY a.timestamp DESC")
    Page<AuditLog> findByTimestampBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    @Query("SELECT a FROM AuditLog a WHERE a.ipAddress = :ipAddress")
    List<AuditLog> findByIpAddress(@Param("ipAddress") String ipAddress);
}
