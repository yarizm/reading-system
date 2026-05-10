package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.BookShare;
import com.example.reading.mapper.BookShareMapper;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.IBookShareService;
import com.example.reading.service.ISysBookService;
import com.example.reading.utils.NotificationWebSocketHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/bookShare")
public class BookShareController {

    @Autowired
    private IBookShareService bookShareService;

    @Autowired
    private BookShareMapper bookShareMapper;

    @Autowired
    private NotificationWebSocketHandler notificationHandler;

    @Autowired
    private ISysBookService sysBookService;

    @Autowired
    private AuthContextService authContextService;

    @PostMapping("/send")
    public Result<?> shareBook(@RequestBody BookShare share, HttpServletRequest request) {
        Long currentUserId = authContextService.currentUserId(request);
        if (currentUserId == null || share.getReceiverId() == null || share.getBookId() == null) {
            return Result.error("403", "Forbidden");
        }
        share.setSenderId(currentUserId);
        share.setIsRead(0);
        bookShareService.save(share);

        Map<String, Object> data = new HashMap<>();
        data.put("senderId", share.getSenderId());
        data.put("bookId", share.getBookId());
        var book = sysBookService.getById(share.getBookId());
        data.put("bookTitle", book != null ? book.getTitle() : "");
        notificationHandler.sendNotification(share.getReceiverId(), "book_share", data);
        return Result.success();
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
