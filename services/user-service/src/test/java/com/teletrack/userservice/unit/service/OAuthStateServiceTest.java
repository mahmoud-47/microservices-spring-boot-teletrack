package com.teletrack.userservice.unit.service;

import com.teletrack.userservice.entity.OAuthState;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.repository.OAuthStateRepository;
import com.teletrack.userservice.service.OAuthStateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
@DisplayName("OAuthStateService Unit Tests")
class OAuthStateServiceTest {

    @Mock
    private OAuthStateRepository oAuthStateRepository;

    @InjectMocks
    private OAuthStateService oAuthStateService;

    @Test
    @DisplayName("Should create OAuth state token for given role")
    void createState_ShouldSaveAndReturnToken() {
        when(oAuthStateRepository.save(any(OAuthState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String token = oAuthStateService.createState(UserRole.OPERATOR);

        assertThat(token).isNotNull().isNotBlank();
        // Should be a UUID format
        assertThat(UUID.fromString(token)).isNotNull();

        ArgumentCaptor<OAuthState> captor = ArgumentCaptor.forClass(OAuthState.class);
        verify(oAuthStateRepository).save(captor.capture());
        OAuthState saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(UserRole.OPERATOR);
        assertThat(saved.getStateToken()).isEqualTo(token);
        assertThat(saved.isUsed()).isFalse();
    }

    @Test
    @DisplayName("Should save correct role for ADMIN")
    void createState_ForAdmin_SavesAdminRole() {
        when(oAuthStateRepository.save(any(OAuthState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        oAuthStateService.createState(UserRole.ADMIN);

        ArgumentCaptor<OAuthState> captor = ArgumentCaptor.forClass(OAuthState.class);
        verify(oAuthStateRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("Should save correct role for SUPPORT")
    void createState_ForSupport_SavesSupportRole() {
        when(oAuthStateRepository.save(any(OAuthState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        oAuthStateService.createState(UserRole.SUPPORT);

        ArgumentCaptor<OAuthState> captor = ArgumentCaptor.forClass(OAuthState.class);
        verify(oAuthStateRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.SUPPORT);
    }

    @Test
    @DisplayName("Should validate and consume valid state token, returning role")
    void validateAndConsumeState_ValidToken_ReturnsRole() {
        OAuthState state = OAuthState.builder()
                .stateToken("valid-token")
                .role(UserRole.OPERATOR)
                .used(false)
                .build();

        when(oAuthStateRepository.findByStateTokenAndUsedFalseAndExpiresAtAfter(
                eq("valid-token"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(state));
        when(oAuthStateRepository.save(any(OAuthState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserRole role = oAuthStateService.validateAndConsumeState("valid-token");

        assertThat(role).isEqualTo(UserRole.OPERATOR);
    }

    @Test
    @DisplayName("Should mark state token as used after consumption")
    void validateAndConsumeState_TokenMarkedUsedAfterConsumption() {
        OAuthState state = OAuthState.builder()
                .stateToken("valid-token")
                .role(UserRole.SUPPORT)
                .used(false)
                .build();

        when(oAuthStateRepository.findByStateTokenAndUsedFalseAndExpiresAtAfter(
                eq("valid-token"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(state));
        when(oAuthStateRepository.save(any(OAuthState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        oAuthStateService.validateAndConsumeState("valid-token");

        ArgumentCaptor<OAuthState> captor = ArgumentCaptor.forClass(OAuthState.class);
        verify(oAuthStateRepository).save(captor.capture());
        assertThat(captor.getValue().isUsed()).isTrue();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for invalid or expired state token")
    void validateAndConsumeState_InvalidToken_ThrowsException() {
        when(oAuthStateRepository.findByStateTokenAndUsedFalseAndExpiresAtAfter(
                eq("bad-token"), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> oAuthStateService.validateAndConsumeState("bad-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired OAuth state");

        verify(oAuthStateRepository, never()).save(any());
    }
}
