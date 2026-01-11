package com.teletrack.incidentservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String jwt = authHeader.substring(7);

            // Validate token
            if (jwtService.isTokenValid(jwt)) {
                // Extract userId from token claims (this is the UUID string)
                final String userId = jwtService.extractUserId(jwt);

                // Extract role from token claims
                final String role = jwtService.extractRole(jwt);

                // CRITICAL: Check both userId AND role are not null
                if (userId != null && role != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    log.debug("Authenticating user with userId: {} and role: {}", userId, role);

                    // Create authority from role
                    List<SimpleGrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + role)
                    );

                    // Create UserDetails with userId as username
                    // The controller will extract this via userDetails.getUsername()
                    UserDetails userDetails = User.builder()
                            .username(userId)  // This is the userId (UUID string) from token
                            .password("")  // Not needed for JWT auth
                            .authorities(authorities)
                            .build();

                    // Create authentication token
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            authorities
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.debug("Successfully authenticated user: {}", userId);
                } else {
                    log.warn("Token validation failed - userId: {}, role: {}", userId, role);
                }
            } else {
                log.warn("Token is not valid or expired");
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }
}