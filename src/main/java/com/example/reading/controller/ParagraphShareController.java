package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.dto.ParagraphShareRequest;
import com.example.reading.entity.ChatMessage;
import com.example.reading.entity.SysBook;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.IChatMessageService;
import com.example.reading.service.ISysBookService;
import com.example.reading.utils.NotificationWebSocketHandler;
import com.example.reading.utils.ShareMessageBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/paragraphShare")
public class ParagraphShareController {

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
        if (currentUserId == null || request == null || request.getReceiverId() == null || request.getBookId() == null
                || request.getChapterIndex() == null || request.getParagraphIndex() == null) {
            return Result.error("500", "Invalid parameters");
        }
        if (!authContextService.areFriends(currentUserId, request.getReceiverId())) {
            return Result.error("403", "Only friends can receive shares");
        }
        request.setSenderId(currentUserId);

        String quote = ShareMessageBuilder.normalizeParagraphText(request.getQuote());
        if (quote.isEmpty()) return Result.error("500", "Shared paragraph cannot be empty");

        SysBook book = sysBookService.getById(request.getBookId());
        if (!authContextService.canAccessBook(book, currentUserId)) {
            return Result.error("403", "Forbidden");
        }
        String bookTitle = book != null ? book.getTitle() : ShareMessageBuilder.DEFAULT_PARAGRAPH_BOOK_TITLE;

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSenderId(currentUserId);
        chatMessage.setReceiverId(request.getReceiverId());
        chatMessage.setContent(ShareMessageBuilder.buildParagraphShareContent(bookTitle, request, quote));
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
}
