package com.teletrack.apigateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Gateway API")
                        .version("1.0")
                        .description("API documentation for Gateway"));
    }

//    @Bean
//    public List<GroupedOpenApi> apis(RouteDefinitionLocator locator) {
//        List<GroupedOpenApi> groups = new ArrayList<>();
//
//        // User Service API
//        groups.add(GroupedOpenApi.builder()
//                .group("user-service")
//                .pathsToMatch("/api/v1/users/**", "/api/v1/auth/**")
//                .build());
//
//        // Incident Service API
//        groups.add(GroupedOpenApi.builder()
//                .group("incident-service")
//                .pathsToMatch("/api/v1/incidents/**")
//                .build());
//
//        // Reporting Service API
//        groups.add(GroupedOpenApi.builder()
//                .group("reporting-service")
//                .pathsToMatch("/api/v1/reports/**")
//                .build());
//
//        // Notification Service API
//        groups.add(GroupedOpenApi.builder()
//                .group("notification-service")
//                .pathsToMatch("/api/v1/notifications/**")
//                .build());
//
//        return groups;
//    }
}