package com.teletrack.incidentservice.client;

import com.teletrack.commonutils.dto.response.UserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Static utility class for calling User Service via Feign
 *
 * Handles all exceptions and provides clean API for services to use.
 * Services just call UserServiceCaller.validateUser(id) - simple!
 */
@Component
@Slf4j
public class UserServiceCaller {

    private static UserServiceClient userServiceClient;

    // Constructor injection to set static field
    public UserServiceCaller(UserServiceClient userServiceClient) {
        UserServiceCaller.userServiceClient = userServiceClient;
    }

    /**
     * Validate if a user exists by ID
     *
     * @param userId User UUID to validate
     * @return true if user exists, false if not found or service unavailable
     *
     * Usage:
     *   boolean exists = UserServiceCaller.validateUser(userId);
     *   if (!exists) throw new ResourceNotFoundException("User not found");
     */
    public static boolean validateUser(UUID userId) {
        if (userId == null) {
            log.warn("Attempted to validate null userId");
            return false;
        }

        try {
            log.debug("Validating user: {}", userId);
            Boolean result = userServiceClient.validateUser(userId);
            log.debug("User {} validation result: {}", userId, result);
            return result != null && result;
        } catch (feign.FeignException.NotFound e) {
            log.debug("User not found: {}", userId);
            return false;
        } catch (feign.FeignException.Forbidden e) {
            log.error("Forbidden when validating user {}: Invalid service key", userId);
            return false;
        } catch (feign.FeignException e) {
            log.error("Feign error validating user {}: {} - {}",
                    userId, e.status(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error validating user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Get user details by ID
     *
     * @param userId User UUID
     * @return Optional containing UserResponse if found, empty if not found or error
     *
     * Usage:
     *   Optional<UserResponse> user = UserServiceCaller.getUserById(userId);
     *   user.ifPresent(u -> log.info("User: {} {}", u.getFirstName(), u.getLastName()));
     */
    public static Optional<UserResponse> getUserById(UUID userId) {
        if (userId == null) {
            log.warn("Attempted to get null userId");
            return Optional.empty();
        }

        try {
            log.debug("Fetching user details: {}", userId);
            UserResponse user = userServiceClient.getUserById(userId);
            log.debug("User {} fetched successfully", userId);
            return Optional.ofNullable(user);
        } catch (feign.FeignException.NotFound e) {
            log.debug("User not found: {}", userId);
            return Optional.empty();
        } catch (feign.FeignException.Forbidden e) {
            log.error("Forbidden when fetching user {}: Invalid service key", userId);
            return Optional.empty();
        } catch (feign.FeignException e) {
            log.error("Feign error fetching user {}: {} - {}",
                    userId, e.status(), e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Unexpected error fetching user {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }
}