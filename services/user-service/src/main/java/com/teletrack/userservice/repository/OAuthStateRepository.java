package com.teletrack.userservice.repository;

import com.teletrack.userservice.entity.OAuthState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthStateRepository extends JpaRepository<OAuthState, UUID> {

    Optional<OAuthState> findByStateTokenAndUsedFalseAndExpiresAtAfter(
            String stateToken,
            LocalDateTime now
    );

    // Cleanup expired tokens
    void deleteByExpiresAtBefore(LocalDateTime now);
}