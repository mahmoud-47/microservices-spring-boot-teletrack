package com.teletrack.userservice.unit.security;

import com.teletrack.userservice.entity.User;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.security.CustomUserDetails;
import com.teletrack.userservice.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import io.jsonwebtoken.ExpiredJwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    // 46-byte key (368 bits) — valid for HMAC-SHA256
    private static final String TEST_SECRET =
            "dGVzdHNlY3JldGtleXRlc3RzZWNyZXRrZXl0ZXN0c2VjcmV0a2V5dGVzdA==";
    private static final long ACCESS_TOKEN_EXPIRATION = 900_000L;    // 15 min
    private static final long REFRESH_TOKEN_EXPIRATION = 604_800_000L; // 7 days

    private JwtService jwtService;
    private UserDetails userDetails;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", ACCESS_TOKEN_EXPIRATION);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", REFRESH_TOKEN_EXPIRATION);

        user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("hashed")
                .role(UserRole.OPERATOR)
                .active(true)
                .approved(true)
                .build();
        userDetails = new CustomUserDetails(user);
    }

    @Test
    @DisplayName("Should generate a non-null access token with 3-part JWT structure")
    void generateAccessToken_ShouldReturnJwtToken() {
        String token = jwtService.generateAccessToken(userDetails, Map.of());

        assertThat(token).isNotNull().isNotBlank();
        assertThat(token.chars().filter(c -> c == '.').count()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should embed correct subject (email) in access token")
    void generateAccessToken_ShouldEmbedCorrectSubject() {
        String token = jwtService.generateAccessToken(userDetails, Map.of());

        assertThat(jwtService.extractUsername(token)).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Should embed extra claims in access token")
    void generateAccessToken_ShouldEmbedExtraClaims() {
        UUID userId = UUID.randomUUID();
        Map<String, Object> claims = Map.of("userId", userId.toString(), "role", "OPERATOR");

        String token = jwtService.generateAccessToken(userDetails, claims);

        String extractedUserId = jwtService.extractClaim(token,
                c -> c.get("userId", String.class));
        assertThat(extractedUserId).isEqualTo(userId.toString());

        String extractedRole = jwtService.extractClaim(token,
                c -> c.get("role", String.class));
        assertThat(extractedRole).isEqualTo("OPERATOR");
    }

    @Test
    @DisplayName("Should generate a valid refresh token")
    void generateRefreshToken_ShouldReturnValidToken() {
        String token = jwtService.generateRefreshToken(userDetails);

        assertThat(token).isNotNull().isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Should return true for valid token")
    void isTokenValid_ValidToken_ReturnsTrue() {
        String token = jwtService.generateAccessToken(userDetails, Map.of());

        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    @DisplayName("Should return false when token belongs to different user")
    void isTokenValid_WrongUser_ReturnsFalse() {
        String token = jwtService.generateAccessToken(userDetails, Map.of());

        User otherUser = User.builder()
                .id(UUID.randomUUID())
                .email("other@example.com")
                .passwordHash("x")
                .role(UserRole.SUPPORT)
                .active(true)
                .approved(true)
                .build();
        UserDetails otherDetails = new CustomUserDetails(otherUser);

        assertThat(jwtService.isTokenValid(token, otherDetails)).isFalse();
    }

    @Test
    @DisplayName("Should throw ExpiredJwtException for expired token (filter catches this)")
    void isTokenValid_ExpiredToken_ThrowsExpiredJwtException() {
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", -1000L);
        String expiredToken = jwtService.generateAccessToken(userDetails, Map.of());
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", ACCESS_TOKEN_EXPIRATION);

        assertThatThrownBy(() -> jwtService.isTokenValid(expiredToken, userDetails))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("Should extract username from token")
    void extractUsername_ShouldReturnCorrectEmail() {
        String token = jwtService.generateAccessToken(userDetails, Map.of());

        assertThat(jwtService.extractUsername(token)).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Should extract custom claim from token")
    void extractClaim_CustomClaim_ShouldReturnValue() {
        String token = jwtService.generateAccessToken(userDetails, Map.of("role", "OPERATOR"));

        String role = jwtService.extractClaim(token, c -> c.get("role", String.class));

        assertThat(role).isEqualTo("OPERATOR");
    }

    @Test
    @DisplayName("Should return configured refresh token expiration")
    void getRefreshTokenExpiration_ShouldReturnConfiguredValue() {
        assertThat(jwtService.getRefreshTokenExpiration()).isEqualTo(REFRESH_TOKEN_EXPIRATION);
    }

    @Test
    @DisplayName("Should return configured access token expiration")
    void getAccessTokenExpiration_ShouldReturnConfiguredValue() {
        assertThat(jwtService.getAccessTokenExpiration()).isEqualTo(ACCESS_TOKEN_EXPIRATION);
    }

    @Test
    @DisplayName("Should throw exception for malformed token")
    void extractUsername_MalformedToken_ThrowsException() {
        assertThatThrownBy(() -> jwtService.extractUsername("not.a.jwt"))
                .isInstanceOf(Exception.class);
    }
}
