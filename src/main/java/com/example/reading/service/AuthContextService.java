package com.example.reading.service;

import com.example.reading.entity.SysUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthContextService {

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private ISysUserService sysUserService;

    public Long currentUserId(HttpServletRequest request) {
        return authTokenService.resolveUserId(request);
    }

    public boolean isAuthenticated(HttpServletRequest request) {
        return currentUserId(request) != null;
    }

    public boolean isSelf(Long userId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        return currentUserId != null && currentUserId.equals(userId);
    }

    public boolean isAdmin(Long userId) {
        if (userId == null) return false;
        SysUser user = sysUserService.getById(userId);
        return user != null && Integer.valueOf(1).equals(user.getRole());
    }

    public boolean isAdmin(HttpServletRequest request) {
        return isAdmin(currentUserId(request));
    }

    public boolean isSelfOrAdmin(Long userId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        return currentUserId != null && (currentUserId.equals(userId) || isAdmin(currentUserId));
    }
}
