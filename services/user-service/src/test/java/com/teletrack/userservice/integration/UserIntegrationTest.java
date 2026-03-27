package com.teletrack.userservice.integration;

import com.teletrack.userservice.entity.User;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.repository.UserRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Tag("integration")
@DisplayName("User Integration Tests")
class UserIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Inserts an active + approved OPERATOR directly into the DB. */
    private User createActiveOperator(String email) {
        return userRepository.save(User.builder()
                .firstName("Op")
                .lastName("User")
                .email(email)
                .passwordHash(passwordEncoder.encode("Test@1234"))
                .role(UserRole.OPERATOR)
                .active(true)
                .approved(true)
                .build());
    }

    /** Inserts an inactive (email not verified) OPERATOR directly into the DB. */
    private User createInactiveOperator(String email) {
        return userRepository.save(User.builder()
                .firstName("Op")
                .lastName("Pending")
                .email(email)
                .passwordHash(passwordEncoder.encode("Test@1234"))
                .role(UserRole.OPERATOR)
                .active(true)      // active=true means email verified
                .approved(false)   // not yet admin-approved
                .build());
    }

    private String uniqueEmail() {
        return UUID.randomUUID().toString().substring(0, 8) + "@user-it.test";
    }

    // ─── GET /users/{id} ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /users/{id} - ADMIN can fetch any user")
    void getUserById_Admin_Returns200() {
        User op = createActiveOperator(uniqueEmail());
        String adminJwt = loginAsAdmin();

        given().header("Authorization", "Bearer " + adminJwt)
                .when().get("/users/{id}", op.getId().toString())
                .then().statusCode(200)
                .body("id", is(op.getId().toString()))
                .body("email", is(op.getEmail()))
                .body("role", is("OPERATOR"));
    }

    @Test
    @DisplayName("GET /users/{id} - unauthenticated returns 401")
    void getUserById_Unauthenticated_Returns401() {
        User op = createActiveOperator(uniqueEmail());

        given().when().get("/users/{id}", op.getId().toString())
                .then().statusCode(401);
    }

    @Test
    @DisplayName("GET /users/{id} - unknown UUID returns 404")
    void getUserById_NotFound_Returns404() {
        String adminJwt = loginAsAdmin();

        given().header("Authorization", "Bearer " + adminJwt)
                .when().get("/users/{id}", UUID.randomUUID().toString())
                .then().statusCode(404)
                .body("success", is(false));
    }

    // ─── GET /users ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /users - ADMIN gets paginated result")
    void getAllUsers_Admin_Returns200() {
        createActiveOperator(uniqueEmail());
        String adminJwt = loginAsAdmin();

        given().header("Authorization", "Bearer " + adminJwt)
                .when().get("/users")
                .then().statusCode(200)
                .body("content", not(empty()))
                .body("totalElements", greaterThanOrEqualTo(1));
    }

    @Test
    @DisplayName("GET /users - OPERATOR returns 403")
    void getAllUsers_Operator_Returns403() {
        User op = createActiveOperator(uniqueEmail());

        String opJwt = given()
                .contentType(ContentType.JSON)
                .body(String.format("{\"email\":\"%s\",\"password\":\"Test@1234\"}", op.getEmail()))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");

        given().header("Authorization", "Bearer " + opJwt)
                .when().get("/users")
                .then().statusCode(403);
    }

    @Test
    @DisplayName("GET /users?role=OPERATOR - filters by role")
    void getAllUsers_WithRoleFilter_ReturnsOnlyMatchingRole() {
        createActiveOperator(uniqueEmail());
        String adminJwt = loginAsAdmin();

        given().header("Authorization", "Bearer " + adminJwt)
                .param("role", "OPERATOR")
                .when().get("/users")
                .then().statusCode(200)
                .body("content", not(empty()));
    }

    // ─── PUT /users/{id} ──────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /users/{id} - ADMIN can update user name")
    void updateUser_Admin_Returns200() {
        User op = createActiveOperator(uniqueEmail());
        String adminJwt = loginAsAdmin();

        given().header("Authorization", "Bearer " + adminJwt)
                .contentType(ContentType.JSON)
                .body("{\"firstName\":\"Updated\",\"lastName\":\"Name\"}")
                .when().put("/users/{id}", op.getId().toString())
                .then().statusCode(200)
                .body("id", is(op.getId().toString()));
    }

    @Test
    @DisplayName("PUT /users/{id} - no fields returns 400")
    void updateUser_NoFields_Returns400() {
        User op = createActiveOperator(uniqueEmail());
        String adminJwt = loginAsAdmin();

        given().header("Authorization", "Bearer " + adminJwt)
                .contentType(ContentType.JSON)
                .body("{}")
                .when().put("/users/{id}", op.getId().toString())
                .then().statusCode(400)
                .body("success", is(false));
    }

    // ─── PATCH /users/{id}/approve ────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /users/{id}/approve - ADMIN approves pending user")
    void approveUser_Admin_Returns200() {
        User pending = createInactiveOperator(uniqueEmail());
        String adminJwt = loginAsAdmin();

        given().header("Authorization", "Bearer " + adminJwt)
                .when().patch("/users/{id}/approve", pending.getId().toString())
                .then().statusCode(200)
                .body("success", is(true));
    }

    @Test
    @DisplayName("PATCH /users/{id}/approve - already approved returns 400")
    void approveUser_AlreadyApproved_Returns400() {
        User op = createActiveOperator(uniqueEmail()); // active+approved
        String adminJwt = loginAsAdmin();

        given().header("Authorization", "Bearer " + adminJwt)
                .when().patch("/users/{id}/approve", op.getId().toString())
                .then().statusCode(400)
                .body("success", is(false));
    }

    // ─── PATCH /users/{id}/deactivate & activate ──────────────────────────────

    @Test
    @DisplayName("PATCH /users/{id}/deactivate then activate - ADMIN lifecycle")
    void deactivateAndActivate_Admin_Returns200() {
        User op = createActiveOperator(uniqueEmail());
        String adminJwt = loginAsAdmin();
        String id = op.getId().toString();

        given().header("Authorization", "Bearer " + adminJwt)
                .when().patch("/users/{id}/deactivate", id)
                .then().statusCode(200).body("success", is(true));

        given().header("Authorization", "Bearer " + adminJwt)
                .when().patch("/users/{id}/activate", id)
                .then().statusCode(200).body("success", is(true));
    }

    // ─── PATCH /users/{id}/role ───────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /users/{id}/role - ADMIN changes role to SUPPORT")
    void changeUserRole_Admin_Returns200() {
        User op = createActiveOperator(uniqueEmail());
        String adminJwt = loginAsAdmin();

        given().header("Authorization", "Bearer " + adminJwt)
                .param("role", "SUPPORT")
                .when().patch("/users/{id}/role", op.getId().toString())
                .then().statusCode(200)
                .body("role", is("SUPPORT"));
    }

    @Test
    @DisplayName("PATCH /users/{id}/role - same role returns 400")
    void changeUserRole_SameRole_Returns400() {
        User op = createActiveOperator(uniqueEmail());
        String adminJwt = loginAsAdmin();

        given().header("Authorization", "Bearer " + adminJwt)
                .param("role", "OPERATOR")
                .when().patch("/users/{id}/role", op.getId().toString())
                .then().statusCode(400)
                .body("success", is(false));
    }

    // ─── DELETE /users/{id} ───────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /users/{id} - ADMIN deletes a different user")
    void deleteUser_Admin_Returns200() {
        User op = createActiveOperator(uniqueEmail());
        String adminJwt = loginAsAdmin();

        given().header("Authorization", "Bearer " + adminJwt)
                .when().delete("/users/{id}", op.getId().toString())
                .then().statusCode(200)
                .body("success", is(true));

        // Confirm deleted
        given().header("Authorization", "Bearer " + adminJwt)
                .when().get("/users/{id}", op.getId().toString())
                .then().statusCode(404);
    }

    @Test
    @DisplayName("DELETE /users/{id} - self-delete returns 400")
    void deleteUser_SelfDelete_Returns400() {
        String adminJwt = loginAsAdmin();
        String adminId = userRepository.findByEmail("admin2@admin.com")
                .orElseThrow().getId().toString();

        given().header("Authorization", "Bearer " + adminJwt)
                .when().delete("/users/{id}", adminId)
                .then().statusCode(400)
                .body("success", is(false));
    }

    // ─── GET /users/validate/{id} (service-to-service) ────────────────────────

    @Test
    @DisplayName("GET /users/validate/{id} - service key returns true for active user")
    void validateUser_ServiceKey_ActiveUser_ReturnsTrue() {
        User op = createActiveOperator(uniqueEmail());

        given().header("X-Service-Key", "test-service-key-secret")
                .when().get("/users/validate/{id}", op.getId().toString())
                .then().statusCode(200)
                .body(is("true"));
    }

    @Test
    @DisplayName("GET /users/validate/{id} - returns false for unknown ID (no exception)")
    void validateUser_ServiceKey_UnknownId_ReturnsFalse() {
        given().header("X-Service-Key", "test-service-key-secret")
                .when().get("/users/validate/{id}", UUID.randomUUID().toString())
                .then().statusCode(200)
                .body(is("false"));
    }

    @Test
    @DisplayName("GET /users/validate/{id} - wrong service key returns 403")
    void validateUser_WrongKey_Returns403() {
        User op = createActiveOperator(uniqueEmail());

        given().header("X-Service-Key", "wrong-key")
                .when().get("/users/validate/{id}", op.getId().toString())
                .then().statusCode(401);
    }
}
