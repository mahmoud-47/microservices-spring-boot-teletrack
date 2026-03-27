package com.teletrack.userservice.unit.util;

import com.teletrack.userservice.util.IpAddressUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("IpAddressUtil Unit Tests")
class IpAddressUtilTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void setRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("Should return UNKNOWN when no request context is set")
    void getClientIpAddress_NoRequestContext_ReturnsUnknown() {
        // No request context set
        assertThat(IpAddressUtil.getClientIpAddress()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("Should return remote address when no proxy headers present")
    void getClientIpAddress_DirectRequest_ReturnsRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        setRequest(request);

        assertThat(IpAddressUtil.getClientIpAddress()).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("Should return first IP from X-Forwarded-For header")
    void getClientIpAddress_XForwardedForMultipleIps_ReturnsFirst() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2, 10.0.0.3");
        request.setRemoteAddr("10.0.0.3");
        setRequest(request);

        assertThat(IpAddressUtil.getClientIpAddress()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("Should return single IP from X-Forwarded-For header")
    void getClientIpAddress_XForwardedForSingleIp_ReturnsIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.1");
        setRequest(request);

        assertThat(IpAddressUtil.getClientIpAddress()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("Should fall through to remoteAddr when X-Forwarded-For is unknown")
    void getClientIpAddress_UnknownHeaderValue_FallsThrough() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "unknown");
        request.setRemoteAddr("172.16.0.1");
        setRequest(request);

        assertThat(IpAddressUtil.getClientIpAddress()).isEqualTo("172.16.0.1");
    }

    @Test
    @DisplayName("Should use Proxy-Client-IP when X-Forwarded-For is absent")
    void getClientIpAddress_ProxyClientIpHeader_ReturnsIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Proxy-Client-IP", "10.10.10.10");
        request.setRemoteAddr("10.10.10.10");
        setRequest(request);

        assertThat(IpAddressUtil.getClientIpAddress()).isEqualTo("10.10.10.10");
    }
}
