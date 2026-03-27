package com.teletrack.userservice.contract;

import com.teletrack.userservice.entity.User;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.integration.BaseIntegrationTest;
import com.teletrack.userservice.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Contract tests for service-to-service endpoints consumed by other microservices.
 *
 * <p>Covers:
 * <ul>
 *   <li>{@code GET /users/validate/{id}} — consumed by incident-service via X-Service-Key</li>
 *   <li>{@code GET /users/{id}} — consumed by notification-service via X-Service-Key</li>
 * </ul>
 */
@Tag("contract")
@DisplayName("UserService Contract Tests")
class UserServiceContractTest extends BaseIntegrationTest {

    private static final String SERVICE_KEY = "test-service-key-secret";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private User createActiveUser(String email, UserRole role) {
        return userRepository.save(User.builder()
                .firstName("Contract")
                .lastName("User")
                .email(email)
                .passwordHash(passwordEncoder.encode("unused"))
                .role(role)
                .active(true)
                .approved(true)
                .build());
    }

    private String uniqueEmail() {
        return UUID.randomUUID().toString().substring(0, 8) + "@contract.test";
    }

    // ─── Contract: GET /users/validate/{id} (incident-service) ───────────────

    @Test
    @DisplayName("incident-service contract: validate active user → true")
    void validateUser_ActiveApproved_ReturnsTrue() {
        User user = createActiveUser(uniqueEmail(), UserRole.OPERATOR);

        given()
                .header("X-Service-Key", SERVICE_KEY)
                .when().get("/users/validate/{id}", user.getId().toString())
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body(is("true"));
    }

    @Test
    @DisplayName("incident-service contract: validate non-existent user → false (no 404)")
    void validateUser_NonExistentUser_ReturnsFalse() {
        given()
                .header("X-Service-Key", SERVICE_KEY)
                .when().get("/users/validate/{id}", UUID.randomUUID().toString())
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body(is("false"));
    }

    @Test
    @DisplayName("incident-service contract: missing service key → 401")
    void validateUser_NoServiceKey_Returns401() {
        User user = createActiveUser(uniqueEmail(), UserRole.OPERATOR);

        given()
                .when().get("/users/validate/{id}", user.getId().toString())
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("incident-service contract: wrong service key → 401")
    void validateUser_WrongServiceKey_Returns401() {
        User user = createActiveUser(uniqueEmail(), UserRole.OPERATOR);

        given()
                .header("X-Service-Key", "wrong-key")
                .when().get("/users/validate/{id}", user.getId().toString())
                .then()
                .statusCode(401);
    }

    // ─── Contract: GET /users/{id} (notification-service) ────────────────────

    @Test
    @DisplayName("notification-service contract: fetch user by id → returns required fields")
    void getUserById_ServiceKey_ReturnsUserResponse() {
        User user = createActiveUser(uniqueEmail(), UserRole.SUPPORT);

        given()
                .header("X-Service-Key", SERVICE_KEY)
                .when().get("/users/{id}", user.getId().toString())
                .then()
                .statusCode(200)
                .body("id", is(user.getId().toString()))
                .body("email", is(user.getEmail()))
                .body("firstName", is("Contract"))
                .body("lastName", is("User"))
                .body("role", is("SUPPORT"))
                .body("isActive", is(true))
                .body("isApproved", is(true));
    }

    @Test
    @DisplayName("notification-service contract: fetch unknown user → 404")
    void getUserById_ServiceKey_UnknownId_Returns404() {
        given()
                .header("X-Service-Key", SERVICE_KEY)
                .when().get("/users/{id}", UUID.randomUUID().toString())
                .then()
                .statusCode(404)
                .body("success", is(false));
    }
}
