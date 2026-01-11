package com.teletrack.userservice.security;

import com.teletrack.userservice.entity.User;
import com.teletrack.userservice.entity.RefreshToken;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.repository.RefreshTokenRepository;
import com.teletrack.userservice.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuth2UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    @Transactional
    public User processOAuth2User(OAuth2User oAuth2User, String role) {
        String email = oAuth2User.getAttribute("email");
        String firstName = oAuth2User.getAttribute("given_name");
        String lastName = oAuth2User.getAttribute("family_name");

        if (email == null) {
            throw new RuntimeException("Email not found from OAuth2 provider");
        }

        // http://localhost:8080/oauth2/authorization/google?state=role:OPERATOR

        // Check if user already exists
        return userRepository.findByEmail(email.toLowerCase())
                .map(existingUser -> {
                    // Update user information if needed
                    if (firstName != null && !firstName.equals(existingUser.getFirstName())) {
                        existingUser.setFirstName(firstName);
                    }
                    if (lastName != null && !lastName.equals(existingUser.getLastName())) {
                        existingUser.setLastName(lastName);
                    }
                    // OAuth2 users are automatically verified
                    if (!existingUser.getActive()) {
                        existingUser.setActive(true);
                    }

                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {

                    User newUser = User.builder()
                            .firstName(firstName != null ? firstName : "User")
                            .lastName(lastName != null ? lastName : "")
                            .email(email.toLowerCase())
                            .passwordHash(null) // OAuth2 users don't have passwords
                            .role(UserRole.valueOf(role))
                            .active(true) // OAuth2 users are automatically verified
                            .approved(false) // still now admin need to approve
                            .build();

                    return userRepository.save(newUser);
                });
    }

    @Transactional
    public String createRefreshToken(User user) {
        String refreshToken = UUID.randomUUID().toString();

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(
                        LocalDateTime.now()
                                .plus(jwtService.getRefreshTokenExpiration(), ChronoUnit.MILLIS)
                )
                .used(false)
                .revoked(false)
                .build();


        refreshTokenRepository.save(refreshTokenEntity);

        return refreshToken;
    }
}