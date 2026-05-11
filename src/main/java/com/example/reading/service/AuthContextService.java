package com.example.reading.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.Friendship;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysUser;
import com.example.reading.mapper.FriendshipMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthContextService {

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    private FriendshipMapper friendshipMapper;

    @Autowired
    private ISysBookService sysBookService;

    public Long currentUserId(HttpServletRequest request) {
        return activeUserId(authTokenService.resolveUserId(request));
    }

    public Long currentUserId(String token) {
        return activeUserId(authTokenService.resolveUserId(token));
    }

    private Long activeUserId(Long userId) {
        if (userId == null) return null;
        SysUser user = sysUserService.getById(userId);
        if (user == null || Integer.valueOf(1).equals(user.getIsBanned())) {
            return null;
        }
        return userId;
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

    public boolean isPublicBook(SysBook book) {
        return book != null && (book.getStatus() == null || Integer.valueOf(2).equals(book.getStatus()));
    }

    public boolean areFriends(Long userId, Long friendId) {
        if (userId == null || friendId == null) return false;
        QueryWrapper<Friendship> query = new QueryWrapper<>();
        query.eq("status", 1)
                .and(w -> w
                        .and(w2 -> w2.eq("user_id", userId).eq("friend_id", friendId))
                        .or(w2 -> w2.eq("user_id", friendId).eq("friend_id", userId)));
        return friendshipMapper.selectCount(query) > 0;
    }

    public boolean canViewBook(Long bookId, HttpServletRequest request) {
        if (bookId == null) return false;
        SysBook book = sysBookService.getById(bookId);
        return canViewBook(book, request);
    }

    public boolean canViewBook(SysBook book, HttpServletRequest request) {
        return canViewBook(book, currentUserId(request));
    }

    public boolean canViewBook(SysBook book, Long currentUserId) {
        if (book == null) return false;
        boolean uploader = currentUserId != null
                && book.getUploaderId() != null
                && book.getUploaderId().equals(currentUserId);
        return isPublicBook(book) || uploader || isAdmin(currentUserId);
    }

    public boolean canAccessBook(SysBook book, Long currentUserId) {
        if (book == null || currentUserId == null) return false;
        boolean uploader = book.getUploaderId() != null && book.getUploaderId().equals(currentUserId);
        return isPublicBook(book) || uploader || isAdmin(currentUserId);
    }
}
