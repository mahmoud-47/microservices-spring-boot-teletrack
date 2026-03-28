package com.teletrack.userservice.config;

import com.teletrack.userservice.security.CustomAuthenticationEntryPoint;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test-only {@link SecurityFilterChain} used in {@code @WebMvcTest} slices.
 *
 * <p>Mirrors the real {@code SecurityConfig} access rules (permitAll paths, role
 * requirements) while avoiding the full infrastructure stack (JWT filters, OAuth2
 * login, etc.) that cannot run without a real data source. Placed at {@code @Order(0)}
 * so it takes precedence over any auto-configured chains.</p>
 *
 * <p>Must be explicitly imported in each {@code @WebMvcTest} class via
 * {@code @Import(TestSecurityConfig.class)}.</p>
 */
@TestConfiguration
@EnableMethodSecurity
public class TestSecurityConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/register",
                                "/auth/login",
                                "/auth/email/confirm",
                                "/auth/email/verify",
                                "/auth/refresh",
                                "/auth/forgot-password",
                                "/auth/reset-password",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/auth/oauth/google",
                                "/users/hello",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/**",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(new CustomAuthenticationEntryPoint()));
        return http.build();
    }
}
