package com.teletrack.incidentservice.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign configuration to add Incident Service's API key to all requests
 * This key will be validated by other services (like User Service)
 */
@Configuration
public class FeignConfig {

    @Value("${service.api-key.incident-service}")
    private String incidentServiceKey;

    @Bean
    public RequestInterceptor serviceKeyInterceptor() {
        return requestTemplate -> {
            // Add Incident Service's key so User Service can validate it
            requestTemplate.header("X-Service-Key", incidentServiceKey);
        };
    }
}