package kr.co.kumsungenc.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {
    private final boolean trustForwardedHeaders;

    public ClientIpResolver(@Value("${app.security.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    public String resolve(HttpServletRequest request) {
        String remote = normalized(request.getRemoteAddr());
        String forwarded = request.getHeader("X-Forwarded-For");
        if (!trustForwardedHeaders || forwarded == null || !isTrustedProxy(remote)) return remote;
        String[] chain = forwarded.split(",");
        for (int i = chain.length - 1; i >= 0; i--) {
            String candidate = normalized(chain[i]);
            if (!isValidAddress(candidate)) continue;
            if (!isTrustedProxy(candidate)) return candidate;
        }
        return remote;
    }

    private String normalized(String value) {
        if (value == null) return "unknown";
        String trimmed = value.trim();
        return trimmed.length() > 64 ? "unknown" : trimmed;
    }

    private boolean isValidAddress(String value) {
        if (value == null || value.isBlank() || value.length() > 64) return false;
        if (value.contains(":")) return value.matches("^[0-9a-fA-F:.%]+$");
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                if (part.isBlank() || Integer.parseInt(part) > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private boolean isTrustedProxy(String ip) {
        if (ip == null) return false;
        if (ip.equals("127.0.0.1") || ip.equals("::1") || ip.startsWith("10.") || ip.startsWith("192.168.")) return true;
        if (!ip.startsWith("172.")) return false;
        String[] parts = ip.split("\\.");
        if (parts.length < 2) return false;
        try {
            int second = Integer.parseInt(parts[1]);
            return second >= 16 && second <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
