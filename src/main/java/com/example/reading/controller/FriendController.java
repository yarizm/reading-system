package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.Friendship;
import com.example.reading.entity.SysUser;
import com.example.reading.mapper.FriendshipMapper;
import com.example.reading.service.IFriendshipService;
import com.example.reading.service.ISysUserService;
import com.example.reading.utils.NotificationWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 好友关系控制器
 * 提供好友申请、接受/拒绝、列表查询、用户搜索及好友删除功能。
 */
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

    /** 发送好友申请（自动检测双向关系，支持被拒绝后重新申请） */
    @PostMapping("/request")
    public Result<?> sendRequest(@RequestBody Friendship friendship) {
        Long userId = friendship.getUserId();
        Long friendId = friendship.getFriendId();

        if (userId.equals(friendId)) {
            return Result.error("500", "不能添加自己为好友");
        }

        QueryWrapper<Friendship> qw = new QueryWrapper<>();
        qw.and(w -> w
                .and(w2 -> w2.eq("user_id", userId).eq("friend_id", friendId))
                .or(w2 -> w2.eq("user_id", friendId).eq("friend_id", userId))
        );
        Friendship existing = friendshipService.getOne(qw);

        if (existing != null) {
            if (existing.getStatus() == 1) {
                return Result.error("500", "你们已经是好友了");
            } else if (existing.getStatus() == 0) {
                return Result.error("500", "好友请求已发送，请等待对方确认");
            } else {
                existing.setStatus(0);
                existing.setUserId(userId);
                existing.setFriendId(friendId);
                friendshipService.updateById(existing);

                sendFriendRequestNotification(userId, friendId);
                return Result.success();
            }
        }

        friendship.setStatus(0);
        friendshipService.save(friendship);

        sendFriendRequestNotification(userId, friendId);
        return Result.success();
    }

    /** 接受好友请求 */
    @PostMapping("/accept/{id}")
    public Result<?> acceptRequest(@PathVariable Long id) {
        Friendship f = friendshipService.getById(id);
        if (f == null) {
            return Result.error("404", "请求不存在");
        }
        f.setStatus(1);
        friendshipService.updateById(f);
        return Result.success();
    }

    /** 拒绝好友请求 */
    @PostMapping("/reject/{id}")
    public Result<?> rejectRequest(@PathVariable Long id) {
        Friendship f = friendshipService.getById(id);
        if (f == null) {
            return Result.error("404", "请求不存在");
        }
        f.setStatus(2);
        friendshipService.updateById(f);
        return Result.success();
    }

    /** 获取好友列表（含用户详情） */
    @GetMapping("/list/{userId}")
    public Result<?> getFriendList(@PathVariable Long userId) {
        return Result.success(friendshipMapper.selectFriendsWithUserInfo(userId));
    }

    /** 获取待处理的好友请求列表 */
    @GetMapping("/pending/{userId}")
    public Result<?> getPendingRequests(@PathVariable Long userId) {
        return Result.success(friendshipMapper.selectPendingRequests(userId));
    }

    /** 删除好友关系 */
    @DeleteMapping("/{id}")
    public Result<?> deleteFriend(@PathVariable Long id) {
        friendshipService.removeById(id);
        return Result.success();
    }

    /** 搜索用户（按用户名或昵称模糊匹配，最多返回 20 条，脱敏密码） */
    @GetMapping("/search")
    public Result<?> searchUsers(@RequestParam String keyword,
                                  @RequestParam(required = false) Long excludeUserId) {
        QueryWrapper<SysUser> qw = new QueryWrapper<>();
        qw.like("username", keyword).or().like("nickname", keyword);
        if (excludeUserId != null) {
            qw.ne("id", excludeUserId);
        }
        qw.last("LIMIT 20");
        List<SysUser> users = sysUserService.list(qw);
        users.forEach(u -> u.setPassword(null));
        return Result.success(users);
    }

    /** 发送好友请求 WebSocket 通知 */
    private void sendFriendRequestNotification(Long senderId, Long receiverId) {
        SysUser sender = sysUserService.getById(senderId);
        Map<String, Object> data = new HashMap<>();
        data.put("fromUserId", senderId);
        data.put("nickname", sender != null ? sender.getNickname() : "");
        notificationHandler.sendNotification(receiverId, "friend_request", data);
    }
}
