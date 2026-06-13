package com.example.reading.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.BookShare;
import com.example.reading.entity.Friendship;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysUser;
import com.example.reading.mapper.BookShareMapper;
import com.example.reading.mapper.FriendshipMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthContextService {

    private static final String CURRENT_USER_ID_ATTRIBUTE = AuthContextService.class.getName() + ".CURRENT_USER_ID";
    private static final Object NO_ACTIVE_USER = new Object();

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    private FriendshipMapper friendshipMapper;

    @Autowired
    private BookShareMapper bookShareMapper;

    @Autowired
    private ISysBookService sysBookService;

    public Long currentUserId(HttpServletRequest request) {
        Object cached = request.getAttribute(CURRENT_USER_ID_ATTRIBUTE);
        if (cached != null) {
            return cached == NO_ACTIVE_USER ? null : (Long) cached;
        }

        Long userId = activeUserId(authTokenService.resolveUserId(request));
        request.setAttribute(CURRENT_USER_ID_ATTRIBUTE, userId == null ? NO_ACTIVE_USER : userId);
        return userId;
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
        if (isPublicBook(book)) return true;
        if (currentUserId == null) return false;
        if (book.getUploaderId() != null && book.getUploaderId().equals(currentUserId)) return true;
        if (isAdmin(currentUserId)) return true;
        if (Integer.valueOf(4).equals(book.getStatus())) return false;
        return hasReceivedBookShare(book.getId(), currentUserId);
    }

    public boolean canAccessBook(SysBook book, Long currentUserId) {
        if (book == null || currentUserId == null) return false;
        boolean uploader = book.getUploaderId() != null && book.getUploaderId().equals(currentUserId);
        return isPublicBook(book) || uploader || isAdmin(currentUserId);
    }

    private boolean hasReceivedBookShare(Long bookId, Long currentUserId) {
        if (bookId == null || currentUserId == null) return false;
        QueryWrapper<BookShare> query = new QueryWrapper<>();
        query.eq("book_id", bookId).eq("receiver_id", currentUserId);
        return bookShareMapper.selectCount(query) > 0;
    }
}
