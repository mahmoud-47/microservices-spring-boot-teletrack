package com.teletrack.userservice.repository;

import com.teletrack.userservice.entity.EmailVerificationToken;
import com.teletrack.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByToken(String token);
    Optional<EmailVerificationToken> findByUserAndUsedIsFalseAndExpiresAtAfter(User user, LocalDateTime now);
    void deleteByExpiresAtBefore(LocalDateTime now);
}
