package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.ChatMessage;
import com.example.reading.mapper.ChatMessageMapper;
import com.example.reading.service.IChatMessageService;
import com.example.reading.utils.NotificationWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    /**
     * 发送消息
     */
    @PostMapping("/send")
    public Result<?> sendMessage(@RequestBody ChatMessage msg) {
        msg.setIsRead(0);
        chatMessageService.save(msg);

        // WebSocket 推送通知给接收方
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("senderId", msg.getSenderId());
        data.put("content", msg.getContent());
        notificationHandler.sendNotification(msg.getReceiverId(), "chat", data);

        return Result.success();
    }

    /**
     * 获取两人聊天记录
     */
    @GetMapping("/history")
    public Result<?> getChatHistory(@RequestParam Long userId,
                                     @RequestParam Long friendId) {
        List<ChatMessage> history = chatMessageMapper.selectChatHistory(userId, friendId);
        return Result.success(history);
    }

    /**
     * 获取未读消息数
     */
    @GetMapping("/unread/{userId}")
    public Result<?> getUnreadCount(@PathVariable Long userId) {
        Long count = chatMessageMapper.selectUnreadCount(userId);
        return Result.success(count);
    }

    /**
     * 标记与某人的消息为已读
     */
    @PostMapping("/read")
    public Result<?> markAsRead(@RequestParam Long userId,
                                 @RequestParam Long senderId) {
        UpdateWrapper<ChatMessage> uw = new UpdateWrapper<>();
        uw.eq("receiver_id", userId)
          .eq("sender_id", senderId)
          .eq("is_read", 0)
          .set("is_read", 1);
        chatMessageService.update(uw);
        return Result.success();
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/conversations/{userId}")
    public Result<?> getConversations(@PathVariable Long userId) {
        List<Map<String, Object>> conversations = chatMessageMapper.selectConversations(userId);
        return Result.success(conversations);
    }
}
