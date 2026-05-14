package com.example.reading.common;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IpUtil {

    private static volatile boolean trustProxyHeaders = false;

    public IpUtil(@Value("${app.security.trust-proxy-headers:false}") boolean trustProxyHeaders) {
        IpUtil.trustProxyHeaders = trustProxyHeaders;
    }

    public static String getClientIp(HttpServletRequest request) {
        String ip = trustProxyHeaders ? getProxyHeaderIp(request) : null;
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return StrUtil.isBlank(ip) ? "unknown" : ip.trim();
    }

    private static String getProxyHeaderIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (StrUtil.isBlank(ip)) {
            return null;
        }
        for (String candidate : ip.split(",")) {
            String trimmed = candidate.trim();
            if (StrUtil.isNotBlank(trimmed) && !"unknown".equalsIgnoreCase(trimmed)) {
                return trimmed;
            }
        }
        return null;
    }
}
