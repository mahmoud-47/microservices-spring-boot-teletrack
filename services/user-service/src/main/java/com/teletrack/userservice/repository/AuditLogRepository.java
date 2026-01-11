package com.teletrack.userservice.repository;

import com.teletrack.userservice.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Find audit logs with optional filters
     * All parameters are nullable for flexible filtering
     */
    @Query("""
            SELECT a FROM AuditLog a 
            WHERE (:userId IS NULL OR a.userId = :userId) 
            AND (:action IS NULL OR a.action = :action) 
            AND (CAST(:dateFrom AS timestamp) IS NULL OR a.timestamp >= :dateFrom) 
            AND (CAST(:dateTo AS timestamp) IS NULL OR a.timestamp <= :dateTo)
            """)
    Page<AuditLog> findWithFilters(
            @Param("userId") UUID userId,
            @Param("action") String action,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable
    );

    /**
     * Find audit logs by user ID
     */
    Page<AuditLog> findByUserId(UUID userId, Pageable pageable);

    /**
     * Find audit logs by action
     */
    Page<AuditLog> findByAction(String action, Pageable pageable);

    /**
     * Find audit logs within a date range
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :startDate AND :endDate")
    Page<AuditLog> findByTimestampBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );
}