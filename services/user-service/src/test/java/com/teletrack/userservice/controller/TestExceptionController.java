package com.teletrack.userservice.controller;

import com.teletrack.commonutils.exception.BadRequestException;
import com.teletrack.commonutils.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.annotation.*;

/**
 * Helper controller used exclusively by {@link GlobalExceptionHandlerTest} to trigger
 * each exception type that {@code GlobalExceptionHandler} is expected to handle.
 *
 * <p>This file must live in the test source root so it is on the test classpath but
 * not bundled into the production JAR.</p>
 */
@RestController
@RequestMapping("/test-exceptions")
class TestExceptionController {

    @PostMapping("/validation")
    public String validateBody(@Valid @RequestBody ValidatedBody body) {
        return "ok";
    }

    @GetMapping("/bad-credentials")
    public String badCredentials() {
        throw new BadCredentialsException("Bad credentials");
    }

    @GetMapping("/auth-exception")
    public String authException() {
        throw new InsufficientAuthenticationException("Insufficient auth");
    }

    @GetMapping("/not-found")
    public String notFound() {
        throw new ResourceNotFoundException("User not found");
    }

    @GetMapping("/bad-request")
    public String badRequest() {
        throw new BadRequestException("Email already registered");
    }

    @GetMapping("/unexpected")
    public String unexpectedError() {
        throw new RuntimeException("Unexpected internal error");
    }

    @Data
    static class ValidatedBody {
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        private String email;
    }
}
