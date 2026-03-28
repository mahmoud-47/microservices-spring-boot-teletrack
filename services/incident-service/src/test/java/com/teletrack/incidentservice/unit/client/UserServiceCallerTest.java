package com.teletrack.incidentservice.unit.client;

import com.teletrack.commonutils.dto.response.UserResponse;
import com.teletrack.incidentservice.client.UserServiceCaller;
import com.teletrack.incidentservice.client.UserServiceClient;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceCaller Unit Tests")
class UserServiceCallerTest {

    @Mock
    private UserServiceClient userServiceClient;

    @BeforeEach
    void setUp() {
        // Inject the mock into the static field via constructor
        new UserServiceCaller(userServiceClient);
    }

    // ─── validateUser ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("validateUser — valid user returns true")
    void validateUser_ValidUser_ReturnsTrue() {
        UUID userId = UUID.randomUUID();
        when(userServiceClient.validateUser(userId)).thenReturn(true);

        assertThat(UserServiceCaller.validateUser(userId)).isTrue();
    }

    @Test
    @DisplayName("validateUser — null userId returns false")
    void validateUser_NullUserId_ReturnsFalse() {
        assertThat(UserServiceCaller.validateUser(null)).isFalse();
    }

    @Test
    @DisplayName("validateUser — client returns null → false")
    void validateUser_NullResponse_ReturnsFalse() {
        UUID userId = UUID.randomUUID();
        when(userServiceClient.validateUser(userId)).thenReturn(null);

        assertThat(UserServiceCaller.validateUser(userId)).isFalse();
    }

    @Test
    @DisplayName("validateUser — client returns false → false")
    void validateUser_FalseResponse_ReturnsFalse() {
        UUID userId = UUID.randomUUID();
        when(userServiceClient.validateUser(userId)).thenReturn(false);

        assertThat(UserServiceCaller.validateUser(userId)).isFalse();
    }

    @Test
    @DisplayName("validateUser — 404 returns false")
    void validateUser_NotFound_ReturnsFalse() {
        UUID userId = UUID.randomUUID();
        when(userServiceClient.validateUser(userId))
                .thenThrow(mock(FeignException.NotFound.class));

        assertThat(UserServiceCaller.validateUser(userId)).isFalse();
    }

    @Test
    @DisplayName("validateUser — 403 returns false")
    void validateUser_Forbidden_ReturnsFalse() {
        UUID userId = UUID.randomUUID();
        when(userServiceClient.validateUser(userId))
                .thenThrow(mock(FeignException.Forbidden.class));

        assertThat(UserServiceCaller.validateUser(userId)).isFalse();
    }

    @Test
    @DisplayName("validateUser — generic Feign error returns false")
    void validateUser_FeignException_ReturnsFalse() {
        UUID userId = UUID.randomUUID();
        when(userServiceClient.validateUser(userId))
                .thenThrow(mock(FeignException.class));

        assertThat(UserServiceCaller.validateUser(userId)).isFalse();
    }

    @Test
    @DisplayName("validateUser — unexpected exception returns false")
    void validateUser_UnexpectedException_ReturnsFalse() {
        UUID userId = UUID.randomUUID();
        when(userServiceClient.validateUser(userId))
                .thenThrow(new RuntimeException("Network error"));

        assertThat(UserServiceCaller.validateUser(userId)).isFalse();
    }

    // ─── getUserById ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getUserById — found returns Optional with user")
    void getUserById_Found_ReturnsUser() {
        UUID userId = UUID.randomUUID();
        UserResponse user = new UserResponse();
        when(userServiceClient.getUserById(userId)).thenReturn(user);

        Optional<UserResponse> result = UserServiceCaller.getUserById(userId);

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("getUserById — null userId returns empty")
    void getUserById_NullUserId_ReturnsEmpty() {
        assertThat(UserServiceCaller.getUserById(null)).isEmpty();
    }

    @Test
    @DisplayName("getUserById — 404 returns empty")
    void getUserById_NotFound_ReturnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(userServiceClient.getUserById(userId))
                .thenThrow(mock(FeignException.NotFound.class));

        assertThat(UserServiceCaller.getUserById(userId)).isEmpty();
    }

    @Test
    @DisplayName("getUserById — 403 returns empty")
    void getUserById_Forbidden_ReturnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(userServiceClient.getUserById(userId))
                .thenThrow(mock(FeignException.Forbidden.class));

        assertThat(UserServiceCaller.getUserById(userId)).isEmpty();
    }

    @Test
    @DisplayName("getUserById — generic Feign error returns empty")
    void getUserById_FeignException_ReturnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(userServiceClient.getUserById(userId))
                .thenThrow(mock(FeignException.class));

        assertThat(UserServiceCaller.getUserById(userId)).isEmpty();
    }

    // ─── getUserEmail ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getUserEmail — found returns Optional with email")
    void getUserEmail_Found_ReturnsEmail() {
        UUID userId = UUID.randomUUID();
        UserResponse user = new UserResponse();
        user.setEmail("test@example.com");
        when(userServiceClient.getUserById(userId)).thenReturn(user);

        Optional<String> result = UserServiceCaller.getUserEmail(userId);

        assertThat(result).contains("test@example.com");
    }

    @Test
    @DisplayName("getUserEmail — 404 returns empty")
    void getUserEmail_NotFound_ReturnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(userServiceClient.getUserById(userId))
                .thenThrow(mock(FeignException.NotFound.class));

        assertThat(UserServiceCaller.getUserEmail(userId)).isEmpty();
    }

    @Test
    @DisplayName("getUserEmail — generic exception returns empty")
    void getUserEmail_Exception_ReturnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(userServiceClient.getUserById(userId))
                .thenThrow(new RuntimeException("error"));

        assertThat(UserServiceCaller.getUserEmail(userId)).isEmpty();
    }
}
