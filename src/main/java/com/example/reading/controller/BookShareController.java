package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.BookShare;
import com.example.reading.entity.ChatMessage;
import com.example.reading.entity.SysBook;
import com.example.reading.mapper.BookShareMapper;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.IBookShareService;
import com.example.reading.service.IChatMessageService;
import com.example.reading.service.ISysBookService;
import com.example.reading.utils.NotificationWebSocketHandler;
import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/bookShare")
public class BookShareController {

    private static final String BOOK_SHARE_PREFIX = "__BOOK_SHARE__";
    private final Gson gson = new Gson();

    @Autowired
    private IBookShareService bookShareService;

    @Autowired
    private IChatMessageService chatMessageService;

    @Autowired
    private BookShareMapper bookShareMapper;

    @Autowired
    private NotificationWebSocketHandler notificationHandler;

    @Autowired
    private ISysBookService sysBookService;

    @Autowired
    private AuthContextService authContextService;

    @PostMapping("/send")
    @Transactional
    public Result<?> shareBook(@RequestBody BookShare share, HttpServletRequest request) {
        Long currentUserId = authContextService.currentUserId(request);
        if (currentUserId == null || share.getReceiverId() == null || share.getBookId() == null) {
            return Result.error("403", "Forbidden");
        }
        if (!authContextService.areFriends(currentUserId, share.getReceiverId())) {
            return Result.error("403", "Only friends can receive shares");
        }
        SysBook book = sysBookService.getById(share.getBookId());
        if (book == null || Integer.valueOf(4).equals(book.getStatus())) {
            return Result.error("403", "Forbidden");
        }
        if (!authContextService.canAccessBook(book, currentUserId)) {
            return Result.error("403", "Forbidden");
        }
        share.setSenderId(currentUserId);
        share.setIsRead(0);
        bookShareService.save(share);

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSenderId(currentUserId);
        chatMessage.setReceiverId(share.getReceiverId());
        chatMessage.setContent(buildChatShareContent(share, book));
        chatMessage.setIsRead(0);
        chatMessage.setCreateTime(LocalDateTime.now());
        chatMessageService.save(chatMessage);

        Map<String, Object> data = new HashMap<>();
        data.put("senderId", share.getSenderId());
        data.put("content", chatMessage.getContent());
        data.put("shareType", "book");
        data.put("shareId", share.getId());
        data.put("bookId", share.getBookId());
        data.put("bookTitle", book != null ? book.getTitle() : "");
        notificationHandler.sendNotification(share.getReceiverId(), "chat", data);
        notificationHandler.sendNotification(share.getReceiverId(), "book_share", data);
        return Result.success();
    }

    private String buildChatShareContent(BookShare share, SysBook book) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("shareId", share.getId());
        payload.put("bookId", share.getBookId());
        payload.put("bookTitle", book == null ? "" : book.getTitle());
        payload.put("bookAuthor", book == null ? "" : book.getAuthor());
        payload.put("coverUrl", book == null ? "" : book.getCoverUrl());
        payload.put("message", share.getMessage() == null ? "" : share.getMessage().trim());
        return BOOK_SHARE_PREFIX + gson.toJson(payload);
    }

    @GetMapping("/received/{userId}")
    public Result<?> getReceivedShares(@PathVariable Long userId, HttpServletRequest request) {
        if (!authContextService.isSelf(userId, request)) return Result.error("403", "Forbidden");
        return Result.success(bookShareMapper.selectSharesWithBookInfo(userId));
    }

    @PostMapping("/read/{id}")
    public Result<?> markAsRead(@PathVariable Long id, HttpServletRequest request) {
        BookShare share = bookShareService.getById(id);
        if (share == null) return Result.success();
        if (!authContextService.isSelf(share.getReceiverId(), request)) return Result.error("403", "Forbidden");
        share.setIsRead(1);
        bookShareService.updateById(share);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteShare(@PathVariable Long id, HttpServletRequest request) {
        BookShare share = bookShareService.getById(id);
        if (share == null) return Result.success();
        Long currentUserId = authContextService.currentUserId(request);
        if (currentUserId == null || (!currentUserId.equals(share.getSenderId()) && !currentUserId.equals(share.getReceiverId()))) {
            return Result.error("403", "Forbidden");
        }
        bookShareService.removeById(id);
        return Result.success();
    }
}
