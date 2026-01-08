package com.teletrack.apigateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/users")
    public ResponseEntity<Map<String, String>> userServiceFallback() {
        Map<String, String> response = new HashMap<>();
        response.put("error", "User Service is temporarily unavailable");
        response.put("message", "Please try again later");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }

    @GetMapping("/incidents")
    public ResponseEntity<Map<String, String>> incidentServiceFallback() {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Incident Service is temporarily unavailable");
        response.put("message", "Please try again later");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }

    @GetMapping("/reporting")
    public ResponseEntity<Map<String, String>> reportingServiceFallback() {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Reporting Service is temporarily unavailable");
        response.put("message", "Please try again later");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }
}
