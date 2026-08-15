package kr.co.kumsungenc.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {
    @Test
    void ignoresForwardedHeaderFromUntrustedPeer() {
        var request=new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.50");
        request.addHeader("X-Forwarded-For","198.51.100.10");
        assertEquals("203.0.113.50",new ClientIpResolver(true).resolve(request));
    }

    @Test
    void selectsRightmostUntrustedAddressBehindTrustedProxyChain() {
        var request=new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Forwarded-For","203.0.113.99, 198.51.100.10, 172.19.0.2");
        assertEquals("198.51.100.10",new ClientIpResolver(true).resolve(request));
    }

    @Test
    void ignoresForwardingWhenDisabled() {
        var request=new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For","198.51.100.10");
        assertEquals("127.0.0.1",new ClientIpResolver(false).resolve(request));
    }
}
