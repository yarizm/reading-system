package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.dto.ParagraphShareRequest;
import com.example.reading.entity.ChatMessage;
import com.example.reading.entity.SysBook;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.IChatMessageService;
import com.example.reading.service.ISysBookService;
import com.example.reading.utils.NotificationWebSocketHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/paragraphShare")
public class ParagraphShareController {

    private static final String PARAGRAPH_SHARE_PREFIX = "__PARAGRAPH_SHARE__";

    @Autowired
    private IChatMessageService chatMessageService;

    @Autowired
    private ISysBookService sysBookService;

    @Autowired
    private NotificationWebSocketHandler notificationHandler;

    @Autowired
    private AuthContextService authContextService;

    @PostMapping("/send")
    public Result<?> shareParagraph(@RequestBody ParagraphShareRequest request, HttpServletRequest httpRequest) {
        Long currentUserId = authContextService.currentUserId(httpRequest);
        if (currentUserId == null || request.getReceiverId() == null || request.getBookId() == null
                || request.getChapterIndex() == null || request.getParagraphIndex() == null) {
            return Result.error("500", "Invalid parameters");
        }
        request.setSenderId(currentUserId);

        String quote = normalizeText(request.getQuote());
        if (quote.isEmpty()) return Result.error("500", "Shared paragraph cannot be empty");

        SysBook book = sysBookService.getById(request.getBookId());
        String bookTitle = book != null ? book.getTitle() : "当前书籍";

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSenderId(currentUserId);
        chatMessage.setReceiverId(request.getReceiverId());
        chatMessage.setContent(buildShareContent(bookTitle, request, quote));
        chatMessage.setIsRead(0);
        chatMessage.setCreateTime(LocalDateTime.now());
        chatMessageService.save(chatMessage);

        Map<String, Object> data = new HashMap<>();
        data.put("senderId", currentUserId);
        data.put("content", chatMessage.getContent());
        data.put("shareType", "paragraph");
        data.put("bookId", request.getBookId());
        data.put("bookTitle", bookTitle);
        notificationHandler.sendNotification(request.getReceiverId(), "chat", data);
        return Result.success();
    }

    private String buildShareContent(String bookTitle, ParagraphShareRequest request, String quote) {
        return PARAGRAPH_SHARE_PREFIX + "{"
                + "\"bookId\":" + request.getBookId() + ","
                + "\"bookTitle\":\"" + escapeJson(bookTitle) + "\","
                + "\"chapterIndex\":" + request.getChapterIndex() + ","
                + "\"paragraphIndex\":" + request.getParagraphIndex() + ","
                + "\"quote\":\"" + escapeJson(truncate(quote, 180)) + "\","
                + "\"message\":\"" + escapeJson(truncate(normalizeText(request.getMessage()), 120)) + "\""
                + "}";
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replace("\r", " ").replace("\n", " ").trim();
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
