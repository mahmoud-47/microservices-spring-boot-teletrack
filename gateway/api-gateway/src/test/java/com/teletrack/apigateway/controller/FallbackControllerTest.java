package com.teletrack.apigateway.controller;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(FallbackController.class)
@ActiveProfiles("test")
@Tag("controller")
class FallbackControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void userServiceFallback_returns503WithErrorMessage() {
        webTestClient.get().uri("/fallback/users")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.error").isEqualTo("User Service is temporarily unavailable")
                .jsonPath("$.message").isEqualTo("Please try again later")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    void incidentServiceFallback_returns503WithErrorMessage() {
        webTestClient.get().uri("/fallback/incidents")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.error").isEqualTo("Incident Service is temporarily unavailable")
                .jsonPath("$.message").isEqualTo("Please try again later");
    }

    @Test
    void reportingServiceFallback_returns503WithErrorMessage() {
        webTestClient.get().uri("/fallback/reporting")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.error").isEqualTo("Reporting Service is temporarily unavailable")
                .jsonPath("$.message").isEqualTo("Please try again later");
    }
}
