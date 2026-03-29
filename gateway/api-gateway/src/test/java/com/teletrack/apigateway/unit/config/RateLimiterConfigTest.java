package com.teletrack.apigateway.unit.config;

import com.teletrack.apigateway.config.RateLimiterConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class RateLimiterConfigTest {

    private final RateLimiterConfig config = new RateLimiterConfig();

    @Test
    void userKeyResolver_withRemoteAddress_returnsHostAddress() {
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(exchange.getRequest()).thenReturn(request);
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("192.168.1.42", 8080));

        KeyResolver resolver = config.userKeyResolver();

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("192.168.1.42")
                .verifyComplete();
    }

    @Test
    void userKeyResolver_withNullRemoteAddress_returnsAnonymous() {
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(exchange.getRequest()).thenReturn(request);
        when(request.getRemoteAddress()).thenReturn(null);

        KeyResolver resolver = config.userKeyResolver();

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("anonymous")
                .verifyComplete();
    }
}
