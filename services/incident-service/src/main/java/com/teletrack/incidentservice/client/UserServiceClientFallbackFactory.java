package com.teletrack.incidentservice.client;

import com.teletrack.commonutils.dto.response.UserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {

    @Override
    public UserServiceClient create(Throwable cause) {
        return new UserServiceClient() {

            @Override
            public Boolean validateUser(UUID id) {
                log.error("Circuit breaker open — validateUser({}) fallback triggered: {}", id, cause.getMessage());
                return false;
            }

            @Override
            public UserResponse getUserById(UUID id) {
                log.error("Circuit breaker open — getUserById({}) fallback triggered: {}", id, cause.getMessage());
                return null;
            }
        };
    }
}
