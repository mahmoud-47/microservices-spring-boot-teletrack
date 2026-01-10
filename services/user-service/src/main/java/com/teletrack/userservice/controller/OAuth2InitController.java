package com.teletrack.userservice.controller;

import com.teletrack.commonutils.dto.response.ApiResponse;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.service.OAuthStateService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/auth/oauth")
@RequiredArgsConstructor
public class OAuth2InitController {

    private final OAuthStateService oAuthStateService;

    @GetMapping("/google")
    public ResponseEntity<?> initiateGoogleOAuth(
            @RequestParam(required = true) String role,
            HttpServletResponse response) throws IOException {

        // Validate role
        UserRole userRole;
        try {
            userRole = UserRole.valueOf(role.toUpperCase());

            // Additional validation for allowed roles
            if (!List.of(UserRole.OPERATOR, UserRole.SUPPORT, UserRole.ADMIN).contains(userRole)) {
                throw new IllegalArgumentException("Role not allowed for OAuth");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("******* Role not correct");
//            response.sendError(
//                    HttpServletResponse.SC_BAD_REQUEST,
//                    "Invalid role. Must be one of: OPERATOR, SUPPORT, ADMIN"
//            );
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(false)
                    .message("Role not correct")
                    .build(), HttpStatus.BAD_REQUEST);
        }


        // Create state token with role
        String stateToken = oAuthStateService.createState(userRole);
        System.out.println("******* Set token = " + stateToken);

        // Redirect to Spring Security OAuth with state parameter
        String redirectUrl = "http://localhost:8080/oauth2/authorization/google?roletoken=" + stateToken;
        response.sendRedirect(redirectUrl);

        return ResponseEntity.ok("OK");
    }
}