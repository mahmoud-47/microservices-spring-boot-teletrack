package com.teletrack.userservice.unit.service;

import com.teletrack.commonutils.dto.request.UpdateUserRequest;
import com.teletrack.commonutils.dto.response.ApiResponse;
import com.teletrack.commonutils.dto.response.PageResponse;
import com.teletrack.commonutils.dto.response.UserResponse;
import com.teletrack.commonutils.exception.BadRequestException;
import com.teletrack.commonutils.exception.ResourceNotFoundException;
import com.teletrack.userservice.entity.User;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.mapper.UserMapper;
import com.teletrack.userservice.repository.AuditLogRepository;
import com.teletrack.userservice.repository.UserRepository;
import com.teletrack.userservice.security.CustomUserDetails;
import com.teletrack.userservice.service.EventPublisher;
import com.teletrack.userservice.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
@DisplayName("UserService Unit Tests")
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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    // ─── getUserById ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return user by id and log audit")
    void getUserById_Success() {
        mockSecurityContext(adminUser);
        UUID targetId = operatorUser.getId();
        UserResponse stubResponse = UserResponse.builder()
                .id(targetId.toString()).email("op@example.com").role("OPERATOR").build();

        when(userRepository.findById(targetId)).thenReturn(Optional.of(operatorUser));
        when(userMapper.toUserResponse(operatorUser)).thenReturn(stubResponse);

        UserResponse result = userService.getUserById(targetId);

        assertThat(result).isEqualTo(stubResponse);
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user not found by id")
    void getUserById_NotFound() {
        mockSecurityContext(adminUser);
        UUID randomId = UUID.randomUUID();
        when(userRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(randomId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with id");
    }

    @Test
    @DisplayName("Should skip audit log when caller has ROLE_SERVICE")
    void getUserById_ServiceCaller_SkipsAuditLog() {
        // Set ROLE_SERVICE authentication (not CustomUserDetails)
        UsernamePasswordAuthenticationToken serviceAuth = new UsernamePasswordAuthenticationToken(
                "incident-service", null,
                List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(serviceAuth);
        SecurityContextHolder.setContext(ctx);

        UserResponse stubResponse = UserResponse.builder()
                .id(operatorUser.getId().toString()).build();
        when(userRepository.findById(operatorUser.getId())).thenReturn(Optional.of(operatorUser));
        when(userMapper.toUserResponse(operatorUser)).thenReturn(stubResponse);

        userService.getUserById(operatorUser.getId());

        verify(auditLogRepository, never()).save(any());
    }

    // ─── getAllUsers ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return paginated users filtered by role")
    void getAllUsers_WithRoleFilter() {
        mockSecurityContext(adminUser);
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> userPage = new PageImpl<>(List.of(operatorUser), pageable, 1);
        UserResponse stubResponse = UserResponse.builder()
                .id(operatorUser.getId().toString()).role("OPERATOR").build();

        when(userRepository.findByRole(UserRole.OPERATOR, pageable)).thenReturn(userPage);
        when(userMapper.toUserResponse(operatorUser)).thenReturn(stubResponse);

        PageResponse<UserResponse> result = userService.getAllUsers(UserRole.OPERATOR, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1L);
        verify(userRepository).findByRole(UserRole.OPERATOR, pageable);
        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Should return all users when no role filter provided")
    void getAllUsers_WithoutRoleFilter() {
        mockSecurityContext(adminUser);
        Pageable pageable = PageRequest.of(0, 20);
        Page<User> userPage = new PageImpl<>(List.of(adminUser, operatorUser), pageable, 2);
        when(userRepository.findAll(pageable)).thenReturn(userPage);
        when(userMapper.toUserResponse(any())).thenReturn(UserResponse.builder().build());

        PageResponse<UserResponse> result = userService.getAllUsers(null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2L);
        verify(userRepository).findAll(pageable);
        verify(userRepository, never()).findByRole(any(), any());
    }

    // ─── updateUser ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should allow admin to update another user's profile")
    void updateUser_AdminCanUpdateOtherUser() {
        mockSecurityContext(adminUser);
        UUID targetId = operatorUser.getId();
        UpdateUserRequest request = new UpdateUserRequest("Updated", "Name");
        UserResponse stubResponse = UserResponse.builder()
                .id(targetId.toString()).firstName("Updated").build();

        when(userRepository.findById(targetId)).thenReturn(Optional.of(operatorUser));
        when(userRepository.save(any())).thenReturn(operatorUser);
        when(userMapper.toUserResponse(any())).thenReturn(stubResponse);

        UserResponse result = userService.updateUser(targetId, request);

        assertThat(result).isNotNull();
        verify(userRepository).save(any());
    }

    @Test
    @DisplayName("Should allow user to update their own profile")
    void updateUser_UserCanUpdateOwnProfile() {
        mockSecurityContext(operatorUser);
        UpdateUserRequest request = new UpdateUserRequest("Self", null);
        when(userRepository.findById(operatorUser.getId())).thenReturn(Optional.of(operatorUser));
        when(userRepository.save(any())).thenReturn(operatorUser);
        when(userMapper.toUserResponse(any())).thenReturn(UserResponse.builder().build());

        assertThat(userService.updateUser(operatorUser.getId(), request)).isNotNull();
        verify(userRepository).save(any());
    }

    @Test
    @DisplayName("Should throw exception when no valid fields provided for update")
    void updateUser_NoFieldsProvided() {
        mockSecurityContext(adminUser);
        UUID targetId = operatorUser.getId();
        UpdateUserRequest request = new UpdateUserRequest(null, null);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(operatorUser));

        assertThatThrownBy(() -> userService.updateUser(targetId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No valid fields provided for update");
    }

    // ─── approveUser edge cases ───────────────────────────────────────────────

    @Test
    @DisplayName("Should throw exception when approving an already approved user")
    void approveUser_AlreadyApproved() {
        mockSecurityContext(adminUser);
        operatorUser.setActive(true);
        operatorUser.setApproved(true);
        when(userRepository.findById(operatorUser.getId())).thenReturn(Optional.of(operatorUser));

        assertThatThrownBy(() -> userService.approveUser(operatorUser.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already approved");
    }

    // ─── deactivateUser ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Should deactivate user and publish event")
    void deactivateUser_Success() {
        mockSecurityContext(adminUser);
        operatorUser.setActive(true);
        UUID targetId = operatorUser.getId();
        when(userRepository.findById(targetId)).thenReturn(Optional.of(operatorUser));
        when(userRepository.save(any())).thenReturn(operatorUser);

        ApiResponse response = userService.deactivateUser(targetId);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).containsIgnoringCase("deactivated");
        verify(eventPublisher).publishEvent(eq("user.deactivated"), any());
    }

    @Test
    @DisplayName("Should throw exception when deactivating already deactivated user")
    void deactivateUser_AlreadyDeactivated() {
        mockSecurityContext(adminUser);
        operatorUser.setActive(false);
        when(userRepository.findById(operatorUser.getId())).thenReturn(Optional.of(operatorUser));

        assertThatThrownBy(() -> userService.deactivateUser(operatorUser.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already deactivated");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when deactivating unknown user")
    void deactivateUser_UserNotFound() {
        mockSecurityContext(adminUser);
        UUID randomId = UUID.randomUUID();
        when(userRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deactivateUser(randomId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── activateUser ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should activate user successfully")
    void activateUser_Success() {
        mockSecurityContext(adminUser);
        operatorUser.setActive(false);
        UUID targetId = operatorUser.getId();
        when(userRepository.findById(targetId)).thenReturn(Optional.of(operatorUser));
        when(userRepository.save(any())).thenReturn(operatorUser);

        ApiResponse response = userService.activateUser(targetId);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).containsIgnoringCase("activated");
        verify(userRepository).save(any());
    }

    @Test
    @DisplayName("Should throw exception when activating already active user")
    void activateUser_AlreadyActive() {
        mockSecurityContext(adminUser);
        operatorUser.setActive(true);
        when(userRepository.findById(operatorUser.getId())).thenReturn(Optional.of(operatorUser));

        assertThatThrownBy(() -> userService.activateUser(operatorUser.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already active");
    }

    // ─── deleteUser ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should delete user successfully")
    void deleteUser_Success() {
        mockSecurityContext(adminUser);
        UUID targetId = operatorUser.getId();
        when(userRepository.findById(targetId)).thenReturn(Optional.of(operatorUser));

        ApiResponse response = userService.deleteUser(targetId);

        assertThat(response.isSuccess()).isTrue();
        verify(userRepository).delete(operatorUser);
    }

    @Test
    @DisplayName("Should throw exception when admin tries to self-delete")
    void deleteUser_CannotSelfDelete() {
        mockSecurityContext(adminUser);
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> userService.deleteUser(adminUser.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot delete your own account");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when deleting unknown user")
    void deleteUser_NotFound() {
        mockSecurityContext(adminUser);
        UUID randomId = UUID.randomUUID();
        when(userRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(randomId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── changeUserRole ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Should change user role successfully")
    void changeUserRole_Success() {
        mockSecurityContext(adminUser);
        operatorUser.setRole(UserRole.OPERATOR);
        UUID targetId = operatorUser.getId();
        UserResponse stubResponse = UserResponse.builder()
                .id(targetId.toString()).role("SUPPORT").build();

        when(userRepository.findById(targetId)).thenReturn(Optional.of(operatorUser));
        when(userRepository.save(any())).thenReturn(operatorUser);
        when(userMapper.toUserResponse(any())).thenReturn(stubResponse);

        UserResponse result = userService.changeUserRole(targetId, UserRole.SUPPORT);

        assertThat(result.getRole()).isEqualTo("SUPPORT");
        verify(userRepository).save(any());
    }

    @Test
    @DisplayName("Should throw exception when changing to the same role")
    void changeUserRole_SameRoleThrows() {
        mockSecurityContext(adminUser);
        operatorUser.setRole(UserRole.OPERATOR);
        when(userRepository.findById(operatorUser.getId())).thenReturn(Optional.of(operatorUser));

        assertThatThrownBy(() -> userService.changeUserRole(operatorUser.getId(), UserRole.OPERATOR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already has role");
    }

    // ─── validateUser ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return true for active and approved user")
    void validateUser_ActiveAndApproved_ReturnsTrue() {
        operatorUser.setActive(true);
        operatorUser.setApproved(true);
        when(userRepository.findById(operatorUser.getId())).thenReturn(Optional.of(operatorUser));

        assertThat(userService.validateUser(operatorUser.getId())).isTrue();
    }

    @Test
    @DisplayName("Should return false for inactive user")
    void validateUser_InactiveUser_ReturnsFalse() {
        operatorUser.setActive(false);
        operatorUser.setApproved(true);
        when(userRepository.findById(operatorUser.getId())).thenReturn(Optional.of(operatorUser));

        assertThat(userService.validateUser(operatorUser.getId())).isFalse();
    }

    @Test
    @DisplayName("Should return false when user not found")
    void validateUser_NotFound_ReturnsFalse() {
        UUID randomId = UUID.randomUUID();
        when(userRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThat(userService.validateUser(randomId)).isFalse();
    }
}