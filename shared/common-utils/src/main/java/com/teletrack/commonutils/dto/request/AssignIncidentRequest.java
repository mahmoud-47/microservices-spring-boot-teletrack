package com.teletrack.commonutils.dto.request;
// change 2
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignIncidentRequest {

    @NotNull(message = "Assigned user ID is required")
    private UUID assignedTo;
}