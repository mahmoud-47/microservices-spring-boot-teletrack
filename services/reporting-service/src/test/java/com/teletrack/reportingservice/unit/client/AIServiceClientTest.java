package com.teletrack.reportingservice.unit.client;

import com.teletrack.reportingservice.client.AIServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("AIServiceClient Unit Tests")
class AIServiceClientTest {

    private AIServiceClient client;

    @BeforeEach
    void setUp() {
        client = new AIServiceClient();
        // Use an unreachable URL to exercise error handling
        ReflectionTestUtils.setField(client, "aiServiceUrl", "http://localhost:19999");
    }

    @Test
    @DisplayName("getInsights: returns error map when AI service is unreachable")
    void getInsights_ServiceUnavailable_ReturnsErrorMap() {
        Map<String, Object> reportData = Map.of("totalIncidents", 5L);

        Map<String, Object> result = client.getInsights(reportData);

        assertThat(result).containsKey("error");
        assertThat(result).containsKey("message");
    }

    @Test
    @DisplayName("getSummary: returns error map when AI service is unreachable")
    void getSummary_ServiceUnavailable_ReturnsErrorMap() {
        Map<String, Object> reportData = Map.of("period", "2024-01-01 to 2024-01-31");

        Map<String, Object> result = client.getSummary(reportData);

        assertThat(result).containsKey("error");
        assertThat(result).containsKey("message");
    }
}
