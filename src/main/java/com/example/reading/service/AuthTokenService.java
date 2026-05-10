package com.example.reading.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class AuthTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${app.auth.token-secret:${random.uuid}}")
    private String tokenSecret;

    @Value("${app.auth.token-ttl-millis:86400000}")
    private long tokenTtlMillis;

    public String createToken(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        long issuedAt = System.currentTimeMillis();
        String payload = userId + "." + issuedAt;
        String signature = sign(payload);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + "." + signature).getBytes(StandardCharsets.UTF_8));
    }

    public Long resolveUserId(HttpServletRequest request) {
        String token = resolveToken(request);
        return resolveUserId(token);
    }

    public Long resolveUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            String payload = parts[0] + "." + parts[1];
            String expectedSignature = sign(payload);
            if (!MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                return null;
            }
            long issuedAt = Long.parseLong(parts[1]);
            if (tokenTtlMillis > 0 && System.currentTimeMillis() - issuedAt > tokenTtlMillis) {
                return null;
            }
            return Long.valueOf(parts[0]);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return request.getHeader("X-User-Token");
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign auth token", e);
        }
    }
}
