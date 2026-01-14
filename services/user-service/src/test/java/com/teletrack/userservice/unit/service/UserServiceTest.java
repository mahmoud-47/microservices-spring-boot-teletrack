package com.teletrack.userservice.unit.service;

import com.teletrack.commonutils.dto.request.UpdateUserRequest;
import com.teletrack.commonutils.dto.response.ApiResponse;
import com.teletrack.commonutils.exception.BadRequestException;
import com.teletrack.userservice.entity.User;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.mapper.UserMapper;
import com.teletrack.userservice.repository.AuditLogRepository;
import com.teletrack.userservice.repository.UserRepository;
import com.teletrack.userservice.security.CustomUserDetails;
import com.teletrack.userservice.service.EventPublisher;
import com.teletrack.userservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserMapper userMapper;
    @Mock private EventPublisher eventPublisher;

    @InjectMocks private UserService userService;

    private User adminUser;
    private User operatorUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder().id(UUID.randomUUID()).role(UserRole.ADMIN).active(true).build();
        operatorUser = User.builder().id(UUID.randomUUID()).role(UserRole.OPERATOR).active(true).build();
    }

    private void mockSecurityContext(User user) {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        CustomUserDetails userDetails = new CustomUserDetails(user);


        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getPrincipal()).thenReturn(userDetails);

        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("Should throw exception when Operator tries to update another user's profile")
    void updateProfile_SecurityConstraint() {
        // Given
        mockSecurityContext(operatorUser);
        UUID targetUserId = UUID.randomUUID(); // Different from operatorUser
        UpdateUserRequest request = new UpdateUserRequest("New", "Name");

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(User.builder().id(targetUserId).build()));

        // When/Then
        assertThatThrownBy(() -> userService.updateUser(targetUserId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only update your own profile");
    }

    @Test
    @DisplayName("Should publish Kafka event when user is approved")
    void approveUser_PublishesEvent() {
        // Given
        mockSecurityContext(adminUser);
        UUID targetId = operatorUser.getId();
        operatorUser.setApproved(false);
        operatorUser.setActive(true); // Email confirmed

        when(userRepository.findById(targetId)).thenReturn(Optional.of(operatorUser));
        when(userRepository.save(any())).thenReturn(operatorUser);

        // When
        ApiResponse response = userService.approveUser(targetId);

        // Then
        assertThat(response.isSuccess()).isTrue();
        verify(eventPublisher).publishEvent(eq("user.approved"), any());
    }

    @Test
    @DisplayName("Should fail approval if user email is not yet confirmed (active=false)")
    void approveUser_RequiresActiveEmail() {
        // Given
        mockSecurityContext(adminUser);
        operatorUser.setActive(false); // Email NOT confirmed
        when(userRepository.findById(any())).thenReturn(Optional.of(operatorUser));

        // When/Then
        assertThatThrownBy(() -> userService.approveUser(operatorUser.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("did not confirm their email");
    }
}