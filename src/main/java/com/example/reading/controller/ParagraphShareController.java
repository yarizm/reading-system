package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.dto.ParagraphShareRequest;
import com.example.reading.entity.ChatMessage;
import com.example.reading.entity.SysBook;
import com.example.reading.service.IChatMessageService;
import com.example.reading.service.ISysBookService;
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
@RequestMapping("/paragraphShare")
public class ParagraphShareController {

    private static final String PARAGRAPH_SHARE_PREFIX = "__PARAGRAPH_SHARE__";

    @Autowired
    private IChatMessageService chatMessageService;

    @Autowired
    private ISysBookService sysBookService;

    @Autowired
    private NotificationWebSocketHandler notificationHandler;

    @PostMapping("/send")
    public Result<?> shareParagraph(@RequestBody ParagraphShareRequest request) {
        if (request.getSenderId() == null
                || request.getReceiverId() == null
                || request.getBookId() == null
                || request.getChapterIndex() == null
                || request.getParagraphIndex() == null) {
            return Result.error("500", "参数不完整");
        }

        String quote = normalizeText(request.getQuote());
        if (quote.isEmpty()) {
            return Result.error("500", "分享段落不能为空");
        }

        SysBook book = sysBookService.getById(request.getBookId());
        String bookTitle = book != null ? book.getTitle() : "当前书籍";

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSenderId(request.getSenderId());
        chatMessage.setReceiverId(request.getReceiverId());
        chatMessage.setContent(buildShareContent(bookTitle, request, quote));
        chatMessage.setIsRead(0);
        chatMessage.setCreateTime(LocalDateTime.now());
        chatMessageService.save(chatMessage);

        Map<String, Object> data = new HashMap<>();
        data.put("senderId", request.getSenderId());
        data.put("content", chatMessage.getContent());
        data.put("shareType", "paragraph");
        data.put("bookId", request.getBookId());
        data.put("bookTitle", bookTitle);
        notificationHandler.sendNotification(request.getReceiverId(), "chat", data);

        return Result.success();
    }

    private String buildShareContent(String bookTitle, ParagraphShareRequest request, String quote) {
        String message = normalizeText(request.getMessage());
        String safeTitle = escapeJson(bookTitle);
        String safeQuote = escapeJson(truncate(quote, 180));
        String safeMessage = escapeJson(truncate(message, 120));

        return PARAGRAPH_SHARE_PREFIX + "{"
                + "\"bookId\":" + request.getBookId() + ","
                + "\"bookTitle\":\"" + safeTitle + "\","
                + "\"chapterIndex\":" + request.getChapterIndex() + ","
                + "\"paragraphIndex\":" + request.getParagraphIndex() + ","
                + "\"quote\":\"" + safeQuote + "\","
                + "\"message\":\"" + safeMessage + "\""
                + "}";
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r", " ")
                .replace("\n", " ")
                .trim();
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
