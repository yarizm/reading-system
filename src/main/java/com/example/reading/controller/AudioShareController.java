package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.dto.AudioShareRequest;
import com.example.reading.entity.ChatMessage;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.IChatMessageService;
import com.example.reading.utils.NotificationWebSocketHandler;
import com.example.reading.utils.ShareMessageBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/audioShare")
public class AudioShareController {

    @Autowired
    private IChatMessageService chatMessageService;

    @Autowired
    private NotificationWebSocketHandler notificationHandler;

    @Autowired
    private AuthContextService authContextService;

    @PostMapping("/send")
    public Result<?> shareAudio(@RequestBody AudioShareRequest request, HttpServletRequest httpRequest) {
        Long currentUserId = authContextService.currentUserId(httpRequest);
        if (currentUserId == null || request == null || request.getReceiverId() == null
                || request.getAudioUrl() == null || request.getAudioUrl().isBlank()) {
            return Result.error("500", "Invalid parameters");
        }
        if (!authContextService.areFriends(currentUserId, request.getReceiverId())) {
            return Result.error("403", "Only friends can receive shares");
        }
        request.setSenderId(currentUserId);

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSenderId(currentUserId);
        chatMessage.setReceiverId(request.getReceiverId());
        chatMessage.setContent(ShareMessageBuilder.buildAudioShareContent(request));
        chatMessage.setIsRead(0);
        chatMessage.setCreateTime(LocalDateTime.now());
        chatMessageService.save(chatMessage);

        Map<String, Object> data = new HashMap<>();
        data.put("senderId", currentUserId);
        data.put("content", chatMessage.getContent());
        data.put("shareType", "audio");
        data.put("title", request.getTitle());
        notificationHandler.sendNotification(request.getReceiverId(), "chat", data);
        return Result.success();
    }
}
