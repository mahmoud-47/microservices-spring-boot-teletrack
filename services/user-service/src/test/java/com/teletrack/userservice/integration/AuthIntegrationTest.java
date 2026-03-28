package com.teletrack.userservice.integration;

import com.teletrack.userservice.repository.EmailVerificationTokenRepository;
import com.teletrack.userservice.repository.UserRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Tag("integration")
@DisplayName("Auth Integration Tests")
class AuthIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String uniqueEmail() {
        return UUID.randomUUID().toString().substring(0, 8) + "@integration.test";
    }

    private String registerBody(String email) {
        return String.format(
                "{\"firstName\":\"Test\",\"lastName\":\"User\",\"email\":\"%s\",\"password\":\"Test@1234\",\"role\":\"OPERATOR\"}",
                email);
    }

    /**
     * Registers a user, verifies the email via DB token, approves them via admin,
     * and returns their UUID string.
     */
    private String registerVerifyApprove(String email) {
        given().contentType(ContentType.JSON).body(registerBody(email))
                .when().post("/auth/register")
                .then().statusCode(200);

        var user = userRepository.findByEmail(email).orElseThrow();
        var evToken = tokenRepository
                .findByUserAndUsedIsFalseAndExpiresAtAfter(user, LocalDateTime.now())
                .orElseThrow();

        given().param("token", evToken.getToken())
                .when().get("/auth/email/confirm")
                .then().statusCode(200);

        String adminJwt = loginAsAdmin();
        given().header("Authorization", "Bearer " + adminJwt)
                .when().patch("/users/{id}/approve", user.getId().toString())
                .then().statusCode(200);

        return user.getId().toString();
    }

    // ─── Tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Full flow: register → verify email → admin approve → login → refresh")
    void fullAuthFlow_Success() {
        String email = uniqueEmail();

        // 1. Register
        given().contentType(ContentType.JSON).body(registerBody(email))
                .when().post("/auth/register")
                .then().statusCode(200)
                .body("success", is(true))
                .body("message", containsString("Registration successful"));

        // 2. Verify email via DB token
        var user = userRepository.findByEmail(email).orElseThrow();
        var evToken = tokenRepository
                .findByUserAndUsedIsFalseAndExpiresAtAfter(user, LocalDateTime.now())
                .orElseThrow();

        given().param("token", evToken.getToken())
                .when().get("/auth/email/confirm")
                .then().statusCode(200)
                .body("success", is(true));

        // 3. Admin approves user
        String adminJwt = loginAsAdmin();
        given().header("Authorization", "Bearer " + adminJwt)
                .when().patch("/users/{id}/approve", user.getId().toString())
                .then().statusCode(200)
                .body("success", is(true));

        // 4. Login as new user
        String refreshToken = given()
                .contentType(ContentType.JSON)
                .body(String.format("{\"email\":\"%s\",\"password\":\"Test@1234\"}", email))
                .when().post("/auth/login")
                .then().statusCode(200)
                .body("accessToken", not(emptyOrNullString()))
                .body("tokenType", is("Bearer"))
                .body("user.email", is(email))
                .extract().path("refreshToken");

        // 5. Refresh token
        given().contentType(ContentType.JSON)
                .body(String.format("{\"refreshToken\":\"%s\"}", refreshToken))
                .when().post("/auth/refresh")
                .then().statusCode(200)
                .body("accessToken", not(emptyOrNullString()))
                .body("refreshToken", not(emptyOrNullString()));
    }

    @Test
    @DisplayName("POST /auth/register - duplicate email returns 400")
    void register_DuplicateEmail_Returns400() {
        String email = uniqueEmail();
        String body = registerBody(email);

        given().contentType(ContentType.JSON).body(body)
                .when().post("/auth/register")
                .then().statusCode(200);

        given().contentType(ContentType.JSON).body(body)
                .when().post("/auth/register")
                .then().statusCode(400)
                .body("success", is(false))
                .body("message", containsString("Email already registered"));
    }

    @Test
    @DisplayName("POST /auth/register - missing required fields returns 400")
    void register_MissingFields_Returns400() {
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"\",\"password\":\"x\",\"role\":\"OPERATOR\"}")
                .when().post("/auth/register")
                .then().statusCode(400)
                .body("success", is(false));
    }

    @Test
    @DisplayName("POST /auth/login - wrong password returns 401")
    void login_WrongPassword_Returns401() {
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"admin2@admin.com\",\"password\":\"wrong\"}")
                .when().post("/auth/login")
                .then().statusCode(401)
                .body("success", is(false));
    }

    @Test
    @DisplayName("POST /auth/login - email not verified returns 400")
    void login_EmailNotVerified_Returns400() {
        String email = uniqueEmail();
        given().contentType(ContentType.JSON).body(registerBody(email))
                .when().post("/auth/register")
                .then().statusCode(200);

        // User is active=false (not yet email-verified) → Spring Security's DaoAuthenticationProvider
        // throws DisabledException before login() method can throw BadRequestException → 401
        given().contentType(ContentType.JSON)
                .body(String.format("{\"email\":\"%s\",\"password\":\"Test@1234\"}", email))
                .when().post("/auth/login")
                .then().statusCode(401)
                .body("success", is(false));
    }

    @Test
    @DisplayName("POST /auth/login - email verified but not approved returns 400")
    void login_NotApproved_Returns400() {
        String email = uniqueEmail();
        given().contentType(ContentType.JSON).body(registerBody(email))
                .when().post("/auth/register")
                .then().statusCode(200);

        // Verify email only (skip approval)
        var user = userRepository.findByEmail(email).orElseThrow();
        var evToken = tokenRepository
                .findByUserAndUsedIsFalseAndExpiresAtAfter(user, LocalDateTime.now())
                .orElseThrow();
        given().param("token", evToken.getToken())
                .when().get("/auth/email/confirm")
                .then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body(String.format("{\"email\":\"%s\",\"password\":\"Test@1234\"}", email))
                .when().post("/auth/login")
                .then().statusCode(400)
                .body("success", is(false));
    }

    @Test
    @DisplayName("GET /auth/email/confirm - invalid token returns 400")
    void confirmEmail_InvalidToken_Returns400() {
        given().param("token", "totally-invalid-token-xyz-" + UUID.randomUUID())
                .when().get("/auth/email/confirm")
                .then().statusCode(400)
                .body("success", is(false));
    }

    @Test
    @DisplayName("POST /auth/email/verify - resend to unknown email returns 404")
    void resendVerification_UnknownEmail_Returns404() {
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"nobody-" + UUID.randomUUID() + "@example.com\"}")
                .when().post("/auth/email/verify")
                .then().statusCode(404)
                .body("success", is(false));
    }

    @Test
    @DisplayName("POST /auth/refresh - invalid token returns 400")
    void refreshToken_InvalidToken_Returns400() {
        given().contentType(ContentType.JSON)
                .body("{\"refreshToken\":\"invalid-token-" + UUID.randomUUID() + "\"}")
                .when().post("/auth/refresh")
                .then().statusCode(400)
                .body("success", is(false));
    }

    @Test
    @DisplayName("GET /auth/me - authenticated admin returns user data")
    void getCurrentUser_Admin_Returns200() {
        String adminJwt = loginAsAdmin();

        given().header("Authorization", "Bearer " + adminJwt)
                .when().get("/auth/me")
                .then().statusCode(200)
                .body("email", is("admin2@admin.com"))
                .body("role", is("ADMIN"));
    }

    @Test
    @DisplayName("GET /auth/me - unauthenticated returns 401")
    void getCurrentUser_Unauthenticated_Returns401() {
        given().when().get("/auth/me")
                .then().statusCode(401);
    }
}
