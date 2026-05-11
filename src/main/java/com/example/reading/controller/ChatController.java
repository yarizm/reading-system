package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.ChatMessage;
import com.example.reading.mapper.ChatMessageMapper;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.IChatMessageService;
import com.example.reading.utils.NotificationWebSocketHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private IChatMessageService chatMessageService;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private NotificationWebSocketHandler notificationHandler;

    @Autowired
    private AuthContextService authContextService;

    @PostMapping("/send")
    public Result<?> sendMessage(@RequestBody ChatMessage msg, HttpServletRequest request) {
        Long currentUserId = authContextService.currentUserId(request);
        if (currentUserId == null || msg.getReceiverId() == null) {
            return Result.error("403", "Forbidden");
        }
        if (!authContextService.areFriends(currentUserId, msg.getReceiverId())) {
            return Result.error("403", "Only friends can receive messages");
        }
        msg.setSenderId(currentUserId);
        msg.setIsRead(0);
        chatMessageService.save(msg);

        Map<String, Object> data = new HashMap<>();
        data.put("senderId", msg.getSenderId());
        data.put("content", msg.getContent());
        notificationHandler.sendNotification(msg.getReceiverId(), "chat", data);
        return Result.success();
    }

    @GetMapping("/history")
    public Result<?> getChatHistory(@RequestParam Long userId,
                                    @RequestParam Long friendId,
                                    HttpServletRequest request) {
        if (!authContextService.isSelf(userId, request)) {
            return Result.error("403", "Forbidden");
        }
        return Result.success(chatMessageMapper.selectChatHistory(userId, friendId));
    }

    @GetMapping("/unread/{userId}")
    public Result<?> getUnreadCount(@PathVariable Long userId, HttpServletRequest request) {
        if (!authContextService.isSelf(userId, request)) {
            return Result.error("403", "Forbidden");
        }
        return Result.success(chatMessageMapper.selectUnreadCount(userId));
    }

    @PostMapping("/read")
    public Result<?> markAsRead(@RequestParam Long userId,
                                @RequestParam Long senderId,
                                HttpServletRequest request) {
        if (!authContextService.isSelf(userId, request)) {
            return Result.error("403", "Forbidden");
        }
        UpdateWrapper<ChatMessage> uw = new UpdateWrapper<>();
        uw.eq("receiver_id", userId)
                .eq("sender_id", senderId)
                .eq("is_read", 0)
                .set("is_read", 1);
        chatMessageService.update(uw);
        return Result.success();
    }

    @GetMapping("/conversations/{userId}")
    public Result<?> getConversations(@PathVariable Long userId, HttpServletRequest request) {
        if (!authContextService.isSelf(userId, request)) {
            return Result.error("403", "Forbidden");
        }
        return Result.success(chatMessageMapper.selectConversations(userId));
    }
}
