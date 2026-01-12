package com.teletrack.incidentservice.repository;

import com.teletrack.commonutils.enums.IncidentStatus;
import com.teletrack.incidentservice.entity.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    Page<Incident> findByStatus(IncidentStatus status, Pageable pageable);

    Page<Incident> findByAssignedTo(UUID assignedTo, Pageable pageable);

    Page<Incident> findByReportedBy(UUID reportedBy, Pageable pageable);
}