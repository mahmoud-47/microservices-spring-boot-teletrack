package com.teletrack.apigateway.unit.config;

import com.teletrack.apigateway.config.SwaggerConfig;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SwaggerConfigTest {

    private final SwaggerConfig config = new SwaggerConfig();

    @Test
    void customOpenAPI_hasCorrectTitle() {
        OpenAPI openAPI = config.customOpenAPI();
        assertNotNull(openAPI);
        assertEquals("Gateway API", openAPI.getInfo().getTitle());
    }

    @Test
    void customOpenAPI_hasCorrectVersion() {
        OpenAPI openAPI = config.customOpenAPI();
        assertEquals("1.0", openAPI.getInfo().getVersion());
    }

    @Test
    void customOpenAPI_hasDescription() {
        OpenAPI openAPI = config.customOpenAPI();
        assertNotNull(openAPI.getInfo().getDescription());
    }
}
