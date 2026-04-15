package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.ChatMessage;
import com.example.reading.mapper.ChatMessageMapper;
import com.example.reading.service.IChatMessageService;
import com.example.reading.utils.NotificationWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 即时聊天控制器
 * 提供消息发送（含 WebSocket 通知）、历史记录查询、未读计数、已读标记及会话列表功能。
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private IChatMessageService chatMessageService;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private NotificationWebSocketHandler notificationHandler;

    /** 发送消息（同时通过 WebSocket 推送通知给接收方） */
    @PostMapping("/send")
    public Result<?> sendMessage(@RequestBody ChatMessage msg) {
        msg.setIsRead(0);
        chatMessageService.save(msg);

        Map<String, Object> data = new HashMap<>();
        data.put("senderId", msg.getSenderId());
        data.put("content", msg.getContent());
        notificationHandler.sendNotification(msg.getReceiverId(), "chat", data);

        return Result.success();
    }

    /** 获取两人之间的聊天记录 */
    @GetMapping("/history")
    public Result<?> getChatHistory(@RequestParam Long userId, @RequestParam Long friendId) {
        return Result.success(chatMessageMapper.selectChatHistory(userId, friendId));
    }

    /** 获取用户未读消息总数 */
    @GetMapping("/unread/{userId}")
    public Result<?> getUnreadCount(@PathVariable Long userId) {
        return Result.success(chatMessageMapper.selectUnreadCount(userId));
    }

    /** 将来自指定发送者的消息标记为已读 */
    @PostMapping("/read")
    public Result<?> markAsRead(@RequestParam Long userId, @RequestParam Long senderId) {
        UpdateWrapper<ChatMessage> uw = new UpdateWrapper<>();
        uw.eq("receiver_id", userId)
          .eq("sender_id", senderId)
          .eq("is_read", 0)
          .set("is_read", 1);
        chatMessageService.update(uw);
        return Result.success();
    }

    /** 获取用户的会话列表 */
    @GetMapping("/conversations/{userId}")
    public Result<?> getConversations(@PathVariable Long userId) {
        return Result.success(chatMessageMapper.selectConversations(userId));
    }
}
