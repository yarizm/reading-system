package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.dto.AudioShareRequest;
import com.example.reading.entity.ChatMessage;
import com.example.reading.service.IChatMessageService;
import com.example.reading.utils.NotificationWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/audioShare")
public class AudioShareController {

    private static final String AUDIO_SHARE_PREFIX = "__AUDIO_SHARE__";

    @Autowired
    private IChatMessageService chatMessageService;

    @Autowired
    private NotificationWebSocketHandler notificationHandler;

    @PostMapping("/send")
    public Result<?> shareAudio(@RequestBody AudioShareRequest request) {
        if (request.getSenderId() == null
                || request.getReceiverId() == null
                || request.getAudioUrl() == null
                || request.getAudioUrl().isBlank()) {
            return Result.error("500", "参数不完整");
        }

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSenderId(request.getSenderId());
        chatMessage.setReceiverId(request.getReceiverId());
        chatMessage.setContent(buildShareContent(request));
        chatMessage.setIsRead(0);
        chatMessage.setCreateTime(LocalDateTime.now());
        chatMessageService.save(chatMessage);

        Map<String, Object> data = new HashMap<>();
        data.put("senderId", request.getSenderId());
        data.put("content", chatMessage.getContent());
        data.put("shareType", "audio");
        data.put("title", request.getTitle());
        notificationHandler.sendNotification(request.getReceiverId(), "chat", data);

        return Result.success();
    }

    private String buildShareContent(AudioShareRequest request) {
        return AUDIO_SHARE_PREFIX + "{"
                + "\"audioUrl\":\"" + escapeJson(request.getAudioUrl()) + "\","
                + "\"title\":\"" + escapeJson(defaultValue(request.getTitle(), "朗读音频")) + "\","
                + "\"sourceType\":\"" + escapeJson(defaultValue(request.getSourceType(), "paragraph")) + "\","
                + "\"bookId\":" + safeNumber(request.getBookId()) + ","
                + "\"chapterIndex\":" + safeNumber(request.getChapterIndex()) + ","
                + "\"paragraphIndex\":" + safeNumber(request.getParagraphIndex()) + ","
                + "\"message\":\"" + escapeJson(defaultValue(request.getMessage(), "")) + "\""
                + "}";
    }

    private String defaultValue(String text, String fallback) {
        return text == null ? fallback : text.trim();
    }

    private String safeNumber(Number value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
