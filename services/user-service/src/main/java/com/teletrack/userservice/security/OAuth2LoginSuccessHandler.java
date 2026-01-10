package com.teletrack.userservice.security;

import com.teletrack.userservice.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuth2UserService oAuth2UserService;
    private final JwtService jwtService;

    @Value("${app.frontend.url}")
    private String frontendUrl;


    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        // Get state from API
        String role = null;
        String state = request.getParameter("state");
        if (state != null && state.startsWith("role:")) {
            role = state.substring(5); // Extract "OPERATOR"
            if(!List.of("OPERATOR", "SUPPORT", "ADMIN").contains(role)){
                String errorUrl = UriComponentsBuilder
                        .fromUriString(frontendUrl + "/oauth2/redirect")
                        .queryParam("success", false)
                        .queryParam("error", String.format("role %s is incorrect", role))
                        .build()
                        .toUriString();

                getRedirectStrategy().sendRedirect(request, response, errorUrl);
                return;

            }
        }else{

//            String errorUrl = UriComponentsBuilder
//                    .fromUriString(frontendUrl + "/oauth2/redirect")
//                    .queryParam("success", false)
//                    .queryParam("error", "You need to specify the role ex. /oauth2/authorization/google?state=role:OPERATOR")
//                    .build()
//                    .toUriString();
//
//            getRedirectStrategy().sendRedirect(request, response, errorUrl);
//            return;
            String googleLogoutUrl = "https://accounts.google.com/Logout?" +
                    "continue=" + URLEncoder.encode(
                    "http://localhost:3000/login?error=role_required",
                    StandardCharsets.UTF_8
            );

            response.sendRedirect(googleLogoutUrl);
            return;
        }

        User user = oAuth2UserService.processOAuth2User(oAuth2User, role);

        // block access if user not approved
        if (!user.getApproved()) {

            SecurityContextHolder.clearContext();

            String errorUrl = UriComponentsBuilder
                    .fromUriString(frontendUrl + "/oauth2/redirect")
                    .queryParam("success", false)
                    .queryParam("error", "You need to be approved before logging in")
                    .build()
                    .toUriString();

            getRedirectStrategy().sendRedirect(request, response, errorUrl);
            return;
        }

        CustomUserDetails userDetails = new CustomUserDetails(user);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("role", user.getRole().toString());

        String accessToken = jwtService.generateAccessToken(userDetails, claims);
        String refreshToken = oAuth2UserService.createRefreshToken(user);


        // Redirect with tokens (for debug purpose but I would have sent HTTP only cookies)
        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/oauth2/redirect")
                .queryParam("success", true)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

}