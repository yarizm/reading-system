package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.BookShare;
import com.example.reading.mapper.BookShareMapper;
import com.example.reading.service.IBookShareService;
import com.example.reading.service.ISysBookService;
import com.example.reading.utils.NotificationWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    /**
     * 分享书籍给好友
     */
    @PostMapping("/send")
    public Result<?> shareBook(@RequestBody BookShare share) {
        share.setIsRead(0);
        bookShareService.save(share);

        // WebSocket 推送书籍分享通知
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("senderId", share.getSenderId());
        data.put("bookId", share.getBookId());
        // 尝试获取书名
        var book = sysBookService.getById(share.getBookId());
        data.put("bookTitle", book != null ? book.getTitle() : "");
        notificationHandler.sendNotification(share.getReceiverId(), "book_share", data);

        return Result.success();
    }

    /**
     * 获取收到的书籍分享列表
     */
    @GetMapping("/received/{userId}")
    public Result<?> getReceivedShares(@PathVariable Long userId) {
        List<Map<String, Object>> shares = bookShareMapper.selectSharesWithBookInfo(userId);
        return Result.success(shares);
    }

    /**
     * 标记分享已读
     */
    @PostMapping("/read/{id}")
    public Result<?> markAsRead(@PathVariable Long id) {
        BookShare share = bookShareService.getById(id);
        if (share != null) {
            share.setIsRead(1);
            bookShareService.updateById(share);
        }
        return Result.success();
    }

    /**
     * 删除收到的分享记录
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteShare(@PathVariable Long id) {
        bookShareService.removeById(id);
        return Result.success();
    }
}
