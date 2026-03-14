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

    /**
     * 发送好友申请
     */
    @PostMapping("/request")
    public Result<?> sendRequest(@RequestBody Friendship friendship) {
        Long userId = friendship.getUserId();
        Long friendId = friendship.getFriendId();

        if (userId.equals(friendId)) {
            return Result.error("500", "不能添加自己为好友");
        }

        // 检查是否已存在 (正向或反向)
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
                // 之前被拒绝过，允许重新申请
                existing.setStatus(0);
                existing.setUserId(userId);
                existing.setFriendId(friendId);
                friendshipService.updateById(existing);

                // WebSocket 推送好友请求通知（重新申请）
                SysUser sender = sysUserService.getById(userId);
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("fromUserId", userId);
                data.put("nickname", sender != null ? sender.getNickname() : "");
                notificationHandler.sendNotification(friendId, "friend_request", data);

                return Result.success();
            }
        }

        friendship.setStatus(0);
        friendshipService.save(friendship);

        // WebSocket 推送好友请求通知
        SysUser sender = sysUserService.getById(userId);
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("fromUserId", userId);
        data.put("nickname", sender != null ? sender.getNickname() : "");
        notificationHandler.sendNotification(friendId, "friend_request", data);

        return Result.success();
    }

    /**
     * 接受好友请求
     */
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

    /**
     * 拒绝好友请求
     */
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

    /**
     * 获取好友列表
     */
    @GetMapping("/list/{userId}")
    public Result<?> getFriendList(@PathVariable Long userId) {
        List<Map<String, Object>> friends = friendshipMapper.selectFriendsWithUserInfo(userId);
        return Result.success(friends);
    }

    /**
     * 获取待处理的好友请求
     */
    @GetMapping("/pending/{userId}")
    public Result<?> getPendingRequests(@PathVariable Long userId) {
        List<Map<String, Object>> pending = friendshipMapper.selectPendingRequests(userId);
        return Result.success(pending);
    }

    /**
     * 删除好友
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteFriend(@PathVariable Long id) {
        friendshipService.removeById(id);
        return Result.success();
    }

    /**
     * 搜索用户 (按用户名或昵称模糊搜索)
     */
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
        // 不返回密码
        users.forEach(u -> u.setPassword(null));
        return Result.success(users);
    }
}
