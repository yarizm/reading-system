package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.Friendship;
import com.example.reading.entity.SysUser;
import com.example.reading.mapper.FriendshipMapper;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.IFriendshipService;
import com.example.reading.service.ISysUserService;
import com.example.reading.utils.NotificationWebSocketHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/friend")
public class FriendController {

    @Autowired
    private IFriendshipService friendshipService;

    @Autowired
    private FriendshipMapper friendshipMapper;

    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    private NotificationWebSocketHandler notificationHandler;

    @Autowired
    private AuthContextService authContextService;

    @PostMapping("/request")
    public Result<?> sendRequest(@RequestBody Friendship friendship, HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        Long friendId = friendship.getFriendId();
        if (userId == null || friendId == null) {
            return Result.error("403", "Forbidden");
        }
        if (userId.equals(friendId)) {
            return Result.error("500", "Cannot add yourself");
        }

        QueryWrapper<Friendship> qw = new QueryWrapper<>();
        qw.and(w -> w
                .and(w2 -> w2.eq("user_id", userId).eq("friend_id", friendId))
                .or(w2 -> w2.eq("user_id", friendId).eq("friend_id", userId)));
        Friendship existing = friendshipService.getOne(qw);

        if (existing != null) {
            if (existing.getStatus() == 1) return Result.error("500", "Already friends");
            if (existing.getStatus() == 0) return Result.error("500", "Request already sent");
            existing.setStatus(0);
            existing.setUserId(userId);
            existing.setFriendId(friendId);
            friendshipService.updateById(existing);
            sendFriendRequestNotification(userId, friendId);
            return Result.success();
        }

        friendship.setUserId(userId);
        friendship.setStatus(0);
        friendshipService.save(friendship);
        sendFriendRequestNotification(userId, friendId);
        return Result.success();
    }

    @PostMapping("/accept/{id}")
    public Result<?> acceptRequest(@PathVariable Long id, HttpServletRequest request) {
        Friendship f = friendshipService.getById(id);
        if (f == null) return Result.error("404", "Request not found");
        if (!authContextService.isSelf(f.getFriendId(), request)) return Result.error("403", "Forbidden");
        f.setStatus(1);
        friendshipService.updateById(f);
        return Result.success();
    }

    @PostMapping("/reject/{id}")
    public Result<?> rejectRequest(@PathVariable Long id, HttpServletRequest request) {
        Friendship f = friendshipService.getById(id);
        if (f == null) return Result.error("404", "Request not found");
        if (!authContextService.isSelf(f.getFriendId(), request)) return Result.error("403", "Forbidden");
        f.setStatus(2);
        friendshipService.updateById(f);
        return Result.success();
    }

    @GetMapping("/list/{userId}")
    public Result<?> getFriendList(@PathVariable Long userId, HttpServletRequest request) {
        if (!authContextService.isSelf(userId, request)) return Result.error("403", "Forbidden");
        return Result.success(friendshipMapper.selectFriendsWithUserInfo(userId));
    }

    @GetMapping("/pending/{userId}")
    public Result<?> getPendingRequests(@PathVariable Long userId, HttpServletRequest request) {
        if (!authContextService.isSelf(userId, request)) return Result.error("403", "Forbidden");
        return Result.success(friendshipMapper.selectPendingRequests(userId));
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteFriend(@PathVariable Long id, HttpServletRequest request) {
        Friendship f = friendshipService.getById(id);
        if (f == null) return Result.error("404", "Friendship not found");
        Long currentUserId = authContextService.currentUserId(request);
        if (currentUserId == null || (!currentUserId.equals(f.getUserId()) && !currentUserId.equals(f.getFriendId()))) {
            return Result.error("403", "Forbidden");
        }
        friendshipService.removeById(id);
        return Result.success();
    }

    @GetMapping("/search")
    public Result<?> searchUsers(@RequestParam String keyword,
                                 @RequestParam(required = false) Long excludeUserId,
                                 HttpServletRequest request) {
        Long currentUserId = authContextService.currentUserId(request);
        if (currentUserId == null) return Result.error("403", "Forbidden");
        QueryWrapper<SysUser> qw = new QueryWrapper<>();
        qw.and(w -> w.like("username", keyword).or().like("nickname", keyword))
                .ne("id", currentUserId);
        qw.last("LIMIT 20");
        List<SysUser> users = sysUserService.list(qw);
        users.forEach(u -> {
            u.setPassword(null);
            u.setToken(null);
        });
        return Result.success(users);
    }

    private void sendFriendRequestNotification(Long senderId, Long receiverId) {
        SysUser sender = sysUserService.getById(senderId);
        Map<String, Object> data = new HashMap<>();
        data.put("fromUserId", senderId);
        data.put("nickname", sender != null ? sender.getNickname() : "");
        notificationHandler.sendNotification(receiverId, "friend_request", data);
    }
}
