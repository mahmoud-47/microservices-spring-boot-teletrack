package com.teletrack.apigateway.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class ObservabilityConfigTest {

    private final ObservabilityConfig config = new ObservabilityConfig();

    private ServerRequestObservationContext contextForPath(String path) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(request.getURI()).thenReturn(URI.create("http://localhost" + path));
        return new ServerRequestObservationContext(request, response, Map.of());
    }

    @Test
    void filter_actuatorPath_returnsFalse() {
        ObservationPredicate predicate = config.observationFilters();
        assertFalse(predicate.test("http.server.requests", contextForPath("/actuator/health")));
    }

    @Test
    void filter_eurekaPathInServerContext_returnsFalse() {
        ObservationPredicate predicate = config.observationFilters();
        assertFalse(predicate.test("http.server.requests", contextForPath("/eureka/apps")));
    }

    @Test
    void filter_normalPath_returnsTrue() {
        ObservationPredicate predicate = config.observationFilters();
        assertTrue(predicate.test("http.server.requests", contextForPath("/api/v1/incidents")));
    }

    @Test
    void filter_springSecurityName_returnsFalse() {
        ObservationPredicate predicate = config.observationFilters();
        assertFalse(predicate.test("spring.security.filterchain", new Observation.Context()));
    }

    @Test
    void filter_eurekaName_returnsFalse() {
        ObservationPredicate predicate = config.observationFilters();
        assertFalse(predicate.test("some.eureka.discovery", new Observation.Context()));
    }

    @Test
    void filter_nonServerRequestContext_normalName_returnsTrue() {
        ObservationPredicate predicate = config.observationFilters();
        assertTrue(predicate.test("http.client.requests", new Observation.Context()));
    }

    @Test
    void observedAspect_isCreated() {
        ObservationRegistry registry = ObservationRegistry.create();
        ObservedAspect aspect = config.observedAspect(registry);
        assertNotNull(aspect);
    }

    @Test
    void otlpExporter_isCreated() {
        OtlpGrpcSpanExporter exporter = config.otlpHttpSpanExporter("http://localhost:4317");
        assertNotNull(exporter);
        exporter.shutdown();
    }
}
