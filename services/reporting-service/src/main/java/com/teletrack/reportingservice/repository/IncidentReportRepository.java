package com.teletrack.reportingservice.repository;

import com.teletrack.reportingservice.document.IncidentReport;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentReportRepository extends MongoRepository<IncidentReport, String> {

    Optional<IncidentReport> findByIncidentId(UUID incidentId);

    boolean existsByIncidentId(UUID incidentId);

    List<IncidentReport> findByStatus(String status);

    List<IncidentReport> findByAssignedTo(UUID assignedTo);

    List<IncidentReport> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("{ 'createdAt' : { $gte: ?0, $lte: ?1 } }")
    List<IncidentReport> findIncidentsInDateRange(LocalDateTime start, LocalDateTime end);

    long countByStatus(String status);

    long countByPriority(String priority);
}