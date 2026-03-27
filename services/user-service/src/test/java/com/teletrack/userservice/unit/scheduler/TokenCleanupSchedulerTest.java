package com.teletrack.userservice.unit.scheduler;

import com.teletrack.userservice.repository.EmailVerificationTokenRepository;
import com.teletrack.userservice.repository.OAuthStateRepository;
import com.teletrack.userservice.repository.RefreshTokenRepository;
import com.teletrack.userservice.scheduler.TokenCleanupScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
@DisplayName("TokenCleanupScheduler Unit Tests")
class TokenCleanupSchedulerTest {

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private OAuthStateRepository oAuthStateRepository;

    @InjectMocks
    private TokenCleanupScheduler tokenCleanupScheduler;

    @Test
    @DisplayName("Should call all three repository cleanup methods")
    void cleanupExpiredTokens_ShouldCallAllThreeRepositories() {
        tokenCleanupScheduler.cleanupExpiredTokens();

        verify(emailVerificationTokenRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
        verify(refreshTokenRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
        verify(oAuthStateRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Should pass current time to repository delete methods")
    void cleanupExpiredTokens_ShouldPassCurrentTimeToRepositories() {
        ArgumentCaptor<LocalDateTime> emailCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> refreshCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> oauthCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        tokenCleanupScheduler.cleanupExpiredTokens();

        verify(emailVerificationTokenRepository).deleteByExpiresAtBefore(emailCaptor.capture());
        verify(refreshTokenRepository).deleteByExpiresAtBefore(refreshCaptor.capture());
        verify(oAuthStateRepository).deleteByExpiresAtBefore(oauthCaptor.capture());

        LocalDateTime now = LocalDateTime.now();
        assertThat(emailCaptor.getValue()).isCloseTo(now, within(3, ChronoUnit.SECONDS));
        assertThat(refreshCaptor.getValue()).isCloseTo(now, within(3, ChronoUnit.SECONDS));
        assertThat(oauthCaptor.getValue()).isCloseTo(now, within(3, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("Should not propagate exception when email token cleanup fails")
    void cleanupExpiredTokens_WhenEmailRepoThrows_DoesNotPropagateException() {
        doThrow(new RuntimeException("DB error"))
                .when(emailVerificationTokenRepository)
                .deleteByExpiresAtBefore(any(LocalDateTime.class));

        assertThatCode(() -> tokenCleanupScheduler.cleanupExpiredTokens())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should not call other cleanup methods when first cleanup fails (single try block)")
    void cleanupExpiredTokens_WhenFirstFails_SubsequentNotCalled() {
        doThrow(new RuntimeException("DB error"))
                .when(emailVerificationTokenRepository)
                .deleteByExpiresAtBefore(any(LocalDateTime.class));

        tokenCleanupScheduler.cleanupExpiredTokens();

        // All three are inside the same try block, so if first throws, others won't run
        verify(refreshTokenRepository, never()).deleteByExpiresAtBefore(any());
        verify(oAuthStateRepository, never()).deleteByExpiresAtBefore(any());
    }
}
