package com.teletrack.reportingservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AIServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    public Map<String, Object> getInsights(Map<String, Object> reportData) {
        log.info("Calling AI service for insights");

        try {
            String url = aiServiceUrl + "/api/insights";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(reportData, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("Error calling AI service", e);
            return Map.of(
                    "error", "Unable to generate insights",
                    "message", e.getMessage()
            );
        }
    }

    public Map<String, Object> getSummary(Map<String, Object> reportData) {
        log.info("Calling AI service for summary");

        try {
            String url = aiServiceUrl + "/api/summary";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(reportData, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("Error calling AI service", e);
            return Map.of(
                    "error", "Unable to generate summary",
                    "message", e.getMessage()
            );
        }
    }
}