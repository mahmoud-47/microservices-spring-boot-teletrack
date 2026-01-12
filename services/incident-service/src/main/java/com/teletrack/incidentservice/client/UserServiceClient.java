package com.teletrack.incidentservice.client;

import com.teletrack.commonutils.dto.response.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Documentation of Feign client for User Service
 * Other microservices should add this to their classpath to call User Service
 *
 * Usage:
 * 1. Add @EnableFeignClients to your main application class
 * 2. Inject UserServiceClient where needed
 * 3. Call methods like: userServiceClient.validateUser(userId)
 */
@FeignClient(name = "user-service", path = "/users")
public interface UserServiceClient {

    @GetMapping("/validate/{id}")
    Boolean validateUser(@PathVariable("id") UUID id);

    @GetMapping("/{id}")
    UserResponse getUserById(@PathVariable("id") UUID id);
}