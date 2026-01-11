package com.teletrack.incidentservice.mapper;

import com.teletrack.commonutils.dto.response.IncidentHistoryResponse;
import com.teletrack.commonutils.dto.response.IncidentResponse;
import com.teletrack.incidentservice.entity.Incident;
import com.teletrack.incidentservice.entity.IncidentHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface IncidentMapper {

    IncidentResponse toIncidentResponse(Incident incident);

    IncidentHistoryResponse toIncidentHistoryResponse(IncidentHistory history);

    List<IncidentHistoryResponse> toIncidentHistoryResponseList(List<IncidentHistory> historyList);
}