//package com.teletrack.userservice.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
//import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
//import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
//
//import jakarta.servlet.http.HttpServletRequest;
//
//@Configuration
//public class OAuth2AuthorizationRequestCustomizer {
//
//    @Bean
//    public OAuth2AuthorizationRequestResolver authorizationRequestResolver() {
//        DefaultOAuth2AuthorizationRequestResolver resolver =
//                new DefaultOAuth2AuthorizationRequestResolver(
//                        new org.springframework.security.oauth2.client.registration.ClientRegistrationRepository() {
//                            // Implementation details
//                        },
//                        "/oauth2/authorization"
//                );
//
//        return request -> {
//            OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);
//            return authorizationRequest != null
//                    ? customizeAuthorizationRequest(authorizationRequest)
//                    : null;
//        };
//    }
//
//    private OAuth2AuthorizationRequest customizeAuthorizationRequest(
//            OAuth2AuthorizationRequest authorizationRequest) {
//
//        return OAuth2AuthorizationRequest
//                .from(authorizationRequest)
//                .additionalParameters(params -> {
//                    params.put("prompt", "select_account");  // Always show account selector
//                    // params.put("prompt", "consent");      // Always show consent screen
//                    // params.put("prompt", "select_account consent"); // Show both
//                })
//                .build();
//    }
//}