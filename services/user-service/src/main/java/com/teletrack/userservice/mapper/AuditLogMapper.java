package com.teletrack.userservice.mapper;

import com.teletrack.userservice.dto.AuditLogResponse;
import com.teletrack.userservice.entity.AuditLog;
import org.springframework.stereotype.Component;

@Component
public class AuditLogMapper {

    public AuditLogResponse toAuditLogResponse(AuditLog auditLog) {
        if (auditLog == null) {
            return null;
        }

        return AuditLogResponse.builder()
                .id(auditLog.getId().toString())
                .userId(auditLog.getUserId().toString())
                .action(auditLog.getAction())
                .ipAddress(auditLog.getIpAddress())
                .timestamp(auditLog.getTimestamp())
                .details(auditLog.getDetails())
                .build();
    }
}