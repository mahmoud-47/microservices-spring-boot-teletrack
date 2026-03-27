package com.teletrack.userservice.unit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teletrack.userservice.security.CustomAuthenticationEntryPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("CustomAuthenticationEntryPoint Unit Tests")
class CustomAuthenticationEntryPointTest {

    private CustomAuthenticationEntryPoint entryPoint;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        entryPoint = new CustomAuthenticationEntryPoint();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should return 401 status with JSON content type")
    void commence_ShouldReturn401StatusWithJsonContentType() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/admin/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();
        InsufficientAuthenticationException ex =
                new InsufficientAuthenticationException("Full auth required");

        entryPoint.commence(request, response, ex);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    @DisplayName("Should include correct fields in JSON response body")
    @SuppressWarnings("unchecked")
    void commence_ShouldWriteCorrectJsonBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/admin/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();
        InsufficientAuthenticationException ex =
                new InsufficientAuthenticationException("Full authentication required");

        entryPoint.commence(request, response, ex);

        Map<String, Object> body = objectMapper.readValue(
                response.getContentAsString(), Map.class);

        assertThat(body.get("success")).isEqualTo(false);
        assertThat(body.get("message")).isEqualTo(
                "Authentication required. Please provide a valid token.");
        assertThat(body.get("error")).isEqualTo("Full authentication required");
        assertThat(body.get("path")).isEqualTo("/admin/stats");
        assertThat(body.get("data")).isNull();
    }

    @Test
    @DisplayName("Should include the request URI path in response")
    @SuppressWarnings("unchecked")
    void commence_ShouldIncludePathInResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/users/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        InsufficientAuthenticationException ex =
                new InsufficientAuthenticationException("Auth needed");

        entryPoint.commence(request, response, ex);

        Map<String, Object> body = objectMapper.readValue(
                response.getContentAsString(), Map.class);

        assertThat(body.get("path")).isEqualTo("/users/123");
    }
}
