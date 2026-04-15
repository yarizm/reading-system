package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.BookShare;
import com.example.reading.mapper.BookShareMapper;
import com.example.reading.service.IBookShareService;
import com.example.reading.service.ISysBookService;
import com.example.reading.utils.NotificationWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 书籍分享控制器
 * 提供好友间书籍推荐分享的发送、接收列表查询、已读标记及删除功能。
 */
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

    /** 分享书籍给好友（同时推送 WebSocket 通知） */
    @PostMapping("/send")
    public Result<?> shareBook(@RequestBody BookShare share) {
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

    /** 获取收到的书籍分享列表（含书籍详情） */
    @GetMapping("/received/{userId}")
    public Result<?> getReceivedShares(@PathVariable Long userId) {
        return Result.success(bookShareMapper.selectSharesWithBookInfo(userId));
    }

    /** 标记分享记录为已读 */
    @PostMapping("/read/{id}")
    public Result<?> markAsRead(@PathVariable Long id) {
        BookShare share = bookShareService.getById(id);
        if (share != null) {
            share.setIsRead(1);
            bookShareService.updateById(share);
        }
        return Result.success();
    }

    /** 删除分享记录 */
    @DeleteMapping("/{id}")
    public Result<?> deleteShare(@PathVariable Long id) {
        bookShareService.removeById(id);
        return Result.success();
    }
}
