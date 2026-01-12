package com.teletrack.userservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Filter to authenticate service-to-service calls using X-Service-Key header
 *
 * This runs BEFORE JwtAuthenticationFilter and sets authentication
 * so JWT validation is skipped for valid service calls.
 */
@Component
@Order(0)  // CRITICAL: Run before JWT filter
@Slf4j
public class ServiceKeyAuthenticationFilter extends OncePerRequestFilter {

    @Value("${service.api-key.incident-service}")
    private String incidentServiceKey;


    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String serviceKey = request.getHeader("X-Service-Key");

        // If no service key or it's not the one for incidents, continue to JWT authentication
        if (serviceKey == null || !serviceKey.equals(incidentServiceKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("Valid service-to-service call");

        // Create authentication with SERVICE role
        // This bypasses JWT and role checks
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "incident-service",  // Principal is the service name
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))  // Grant SERVICE role
        );

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("Service authenticated successfully");

        // Continue - JWT filter will see authentication is already set and skip
        filterChain.doFilter(request, response);
    }
}