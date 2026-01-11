package com.teletrack.userservice.scheduler;

import com.teletrack.userservice.repository.EmailVerificationTokenRepository;
import com.teletrack.userservice.repository.OAuthStateRepository;
import com.teletrack.userservice.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupScheduler {

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OAuthStateRepository oAuthStateRepository;

    // Run every day at 2 AM
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Starting token cleanup job");

        LocalDateTime now = LocalDateTime.now();

        try {
            emailVerificationTokenRepository.deleteByExpiresAtBefore(now);
            log.info("Cleaned up expired email verification tokens");

            refreshTokenRepository.deleteByExpiresAtBefore(now);
            log.info("Cleaned up expired refresh tokens");

            oAuthStateRepository.deleteByExpiresAtBefore(now);
            log.info("Cleaned up expired OAuth tokens");

            log.info("Token cleanup job completed successfully");
        } catch (Exception e) {
            log.error("Error during token cleanup: {}", e.getMessage());
        }
    }
}