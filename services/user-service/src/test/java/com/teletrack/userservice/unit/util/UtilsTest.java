package com.teletrack.userservice.unit.util;

import com.teletrack.commonutils.dto.response.UserResponse;
import com.teletrack.userservice.entity.User;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.mapper.UserMapper;
import com.teletrack.userservice.security.CustomUserDetails;
import com.teletrack.userservice.util.Utils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
@DisplayName("Utils Unit Tests")
class UtilsTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private Utils utils;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should return UserResponse for currently authenticated user")
    void getCurrentUser_ReturnsUserResponse() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .role(UserRole.OPERATOR)
                .active(true)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);

        UserResponse stub = UserResponse.builder()
                .id(user.getId().toString())
                .email("test@example.com")
                .role("OPERATOR")
                .build();
        when(userMapper.mapToUserResponse(user)).thenReturn(stub);

        UserResponse result = utils.getCurrentUser();

        assertThat(result).isEqualTo(stub);
        verify(userMapper).mapToUserResponse(user);
    }
}
