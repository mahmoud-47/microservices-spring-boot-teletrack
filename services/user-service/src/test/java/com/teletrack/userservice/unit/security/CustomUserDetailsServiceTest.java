package com.teletrack.userservice.unit.security;

import com.teletrack.userservice.entity.User;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.repository.UserRepository;
import com.teletrack.userservice.security.CustomUserDetails;
import com.teletrack.userservice.security.CustomUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
@DisplayName("CustomUserDetailsService Unit Tests")
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("Should load user details for existing user")
    void loadUserByUsername_UserFound_ReturnsCustomUserDetails() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("hashed_password")
                .role(UserRole.OPERATOR)
                .active(true)
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        UserDetails result = customUserDetailsService.loadUserByUsername("test@example.com");

        assertThat(result).isInstanceOf(CustomUserDetails.class);
        assertThat(result.getUsername()).isEqualTo("test@example.com");
        assertThat(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OPERATOR"))).isTrue();
        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should throw UsernameNotFoundException when user not found")
    void loadUserByUsername_UserNotFound_ThrowsUsernameNotFoundException() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                customUserDetailsService.loadUserByUsername("notfound@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("notfound@example.com");
    }

    @Test
    @DisplayName("Should return isEnabled=false for inactive user")
    void loadUserByUsername_InactiveUser_IsEnabledFalse() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("inactive@example.com")
                .passwordHash("x")
                .role(UserRole.SUPPORT)
                .active(false)
                .build();

        when(userRepository.findByEmail("inactive@example.com")).thenReturn(Optional.of(user));

        UserDetails result = customUserDetailsService.loadUserByUsername("inactive@example.com");

        assertThat(result.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should return ROLE_ADMIN authority for admin user")
    void loadUserByUsername_AdminUser_HasAdminAuthority() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .passwordHash("x")
                .role(UserRole.ADMIN)
                .active(true)
                .build();

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(user));

        UserDetails result = customUserDetailsService.loadUserByUsername("admin@example.com");

        assertThat(result.getAuthorities()).hasSize(1);
        assertThat(result.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_ADMIN");
    }
}
