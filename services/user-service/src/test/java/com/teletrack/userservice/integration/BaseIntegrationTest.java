package com.teletrack.userservice.integration;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static io.restassured.RestAssured.given;

/**
 * Abstract base for all integration tests.
 *
 * <p>Starts a shared PostgreSQL 16 and Kafka container once per JVM, wires them
 * into the Spring context via {@link DynamicPropertySource}, and configures REST
 * Assured to point at the random server port before each test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "spring.security.oauth2.client.registration.google.scope=openid,email,profile",
        "spring.liquibase.change-log=classpath:/db/changelog/db.changelog-master.xml",
        "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.logging.OpenTelemetryLoggingAutoConfiguration",
        "jwt.secret=dGVzdHNlY3JldGtleXRlc3RzZWNyZXRrZXl0ZXN0c2VjcmV0a2V5dGVzdA==",
        "jwt.access-token.expiration=900000",
        "jwt.refresh-token.expiration=604800000",
        "service.api-key.incident-service=test-service-key-secret",
        "app.base-url=http://localhost:8080",
        "app.frontend.url=http://localhost:3000",
        "tracing.url=http://localhost:4317",
        "management.tracing.enabled=false"
})
public abstract class BaseIntegrationTest {

    // ─── Shared containers (singleton — started once per JVM) ────────────────

    static final PostgreSQLContainer<?> postgres;
    static final KafkaContainer kafka;

    static {
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));
        postgres.start();
        kafka.start();
    }

    @DynamicPropertySource
    static void overrideContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    // ─── REST Assured setup ──────────────────────────────────────────────────

    @LocalServerPort
    int port;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    // ─── Shared helper ───────────────────────────────────────────────────────

    /**
     * Logs in as the pre-seeded admin user (admin2@admin.com / admin) and
     * returns the JWT access token.
     */
    protected String loginAsAdmin() {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"admin2@admin.com\",\"password\":\"admin\"}")
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");
    }
}
