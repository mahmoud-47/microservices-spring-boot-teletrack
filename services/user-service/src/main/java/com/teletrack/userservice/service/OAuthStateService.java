package com.teletrack.userservice.service;

import com.teletrack.userservice.entity.OAuthState;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.repository.OAuthStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthStateService {

    private final OAuthStateRepository oAuthStateRepository;

    /**
     * Create a new OAuth state token with associated role
     */
    @Transactional
    public String createState(UserRole role) {
        String stateToken = UUID.randomUUID().toString();

        OAuthState oauthState = OAuthState.builder()
                .stateToken(stateToken)
                .role(role)
                .build();

        oAuthStateRepository.save(oauthState);

        log.info("Created OAuth state token for role: {}", role);
        return stateToken;
    }

    /**
     * Validate and consume OAuth state token
     * Returns the role if valid, throws exception otherwise
     */
    @Transactional
    public UserRole validateAndConsumeState(String stateToken) {
        Optional<OAuthState> oauthState = oAuthStateRepository
                .findByStateTokenAndUsedFalseAndExpiresAtAfter(
                        stateToken,
                        LocalDateTime.now()
                );

        if (oauthState.isEmpty()) {
            log.warn("Invalid or expired OAuth state token: {}", stateToken);
            throw new IllegalArgumentException("Invalid or expired OAuth state");
        }

        OAuthState state = oauthState.get();

        // Mark as used (one-time use only)
        state.setUsed(true);
        oAuthStateRepository.save(state);

        log.info("OAuth state token consumed for role: {}", state.getRole());
        return state.getRole();
    }

    /**
     * Cleanup expired tokens daily
     */
    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM daily
    @Transactional
    public void cleanupExpiredStates() {
        oAuthStateRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.info("Cleaned up expired OAuth state tokens");
    }
}