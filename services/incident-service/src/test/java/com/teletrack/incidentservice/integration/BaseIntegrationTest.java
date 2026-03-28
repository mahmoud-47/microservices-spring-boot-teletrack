package com.teletrack.incidentservice.integration;

import com.teletrack.incidentservice.client.UserServiceClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "jwt.secret=dGVzdHNlY3JldGtleXRlc3RzZWNyZXRrZXl0ZXN0c2VjcmV0a2V5dGVzdA==",
        "tracing.url=http://localhost:4317",
        "management.tracing.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.logging.OpenTelemetryLoggingAutoConfiguration",
        "spring.liquibase.change-log=classpath:/db/changelog/db.changelog-master.xml",
        "service.api-key.incident-service=test-service-key-secret"
})
public abstract class BaseIntegrationTest {

    private static final String TEST_SECRET =
            "dGVzdHNlY3JldGtleXRlc3RzZWNyZXRrZXl0ZXN0c2VjcmV0a2V5dGVzdA==";

    // Containers started once per JVM — shared across all subclasses.
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    static {
        postgres.start();
        kafka.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    // Replace the Feign client so tests don't try to call the real user-service.
    // Subclasses can stub this via Mockito.when().
    @MockitoBean
    protected UserServiceClient userServiceClient;

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "";
    }

    protected String generateJwt(String userId, String role) {
        byte[] keyBytes = Decoders.BASE64.decode(TEST_SECRET);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.builder()
                .claim("userId", userId)
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key)
                .compact();
    }

    protected String adminJwt() {
        return generateJwt(UUID.randomUUID().toString(), "ADMIN");
    }

    protected String operatorJwt() {
        return generateJwt(UUID.randomUUID().toString(), "OPERATOR");
    }

    protected String supportJwt() {
        return generateJwt(UUID.randomUUID().toString(), "SUPPORT");
    }
}
