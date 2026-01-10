package com.teletrack.userservice.security;

import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.entity.User;
import com.teletrack.userservice.service.OAuthStateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuth2UserService oAuth2UserService;
    private final JwtService jwtService;
    private final OAuthStateService oAuthStateService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        String state = request.getParameter("state");
        String stateToken = null;

        if (state != null && state.contains("::")) {
            String[] parts = state.split("::", 2);
            stateToken = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
        }

        // roletoken is now available
        log.debug("Received roletoken: {}", stateToken);

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        if (stateToken == null || stateToken.isEmpty()) {
            log.warn("OAuth callback missing state parameter");
            redirectWithError(request, response, "Missing state parameter. Please initiate login via /auth/oauth/google?role=OPERATOR");
            return;
        }

        // Validate state and get role
        UserRole role;
        try {
            role = oAuthStateService.validateAndConsumeState(stateToken);
            System.out.println("******* Login role = " + role);
        } catch (IllegalArgumentException e) {
            System.out.println("******* !Login role = ");
            log.warn("Invalid OAuth state token: {}", stateToken);
            redirectWithError(request, response, "Invalid or expired login session. Please try again.");
            return;
        }

        // Process OAuth2 user with role
        User user;
        try {
            user = oAuth2UserService.processOAuth2User(oAuth2User, role.name());
        } catch (Exception e) {
            log.error("Error processing OAuth user", e);
            redirectWithError(request, response, "Error creating user account. Please try again.");
            return;
        }

        // Check if user is approved
        if (!user.getApproved()) {
            SecurityContextHolder.clearContext();
            log.warn("Unapproved user attempted login: {}", user.getEmail());
            redirectWithError(request, response, "Your account is pending approval");
            return;
        }

        // Generate tokens
        CustomUserDetails userDetails = new CustomUserDetails(user);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("role", user.getRole().toString());

        String accessToken = jwtService.generateAccessToken(userDetails, claims);
        String refreshToken = oAuth2UserService.createRefreshToken(user);

        log.info("OAuth login successful for user: {} with role: {}", user.getEmail(), role);

        // Redirect with tokens
        String targetUrl = UriComponentsBuilder
                .fromUriString(frontendUrl + "/oauth2/redirect")
                .queryParam("success", true)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private void redirectWithError(HttpServletRequest request,HttpServletResponse response, String errorMessage) throws IOException {
        String errorUrl = UriComponentsBuilder
                .fromUriString(frontendUrl + "/oauth2/redirect")
                .queryParam("success", false)
                .queryParam("error", errorMessage)
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, errorUrl);
    }
}