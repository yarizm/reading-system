package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.UserBookshelf;
import com.example.reading.mapper.UserBookshelfMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bookshelf")
public class UserBookshelfController {

    @Autowired
    private UserBookshelfMapper shelfMapper;

    /**
     * 加入书架
     */
    @PostMapping("/add")
    public Result<?> add(@RequestBody UserBookshelf shelf) {
        // 1. 检查是否已存在 (利用唯一索引 uk_user_book 其实数据库会报错，但这里先查一下更优雅)
        QueryWrapper<UserBookshelf> query = new QueryWrapper<>();
        query.eq("user_id", shelf.getUserId()).eq("book_id", shelf.getBookId());

        if (shelfMapper.selectCount(query) > 0) {
            return Result.error("500", "这本书已经在你的书架里了");
        }

        shelf.setLastReadTime(LocalDateTime.now());
        shelf.setProgressIndex(0);
        shelf.setIsFinished(0);
        shelfMapper.insert(shelf);
        return Result.success();
    }

    /**
     * 获取我的书架 (包含书籍详情)
     */
    @GetMapping("/list/{userId}")
    public Result<List<Map<String, Object>>> list(@PathVariable Long userId) {
        List<Map<String, Object>> list = shelfMapper.selectMyShelf(userId);
        return Result.success(list);
    }


    /**
     * 移出书架
     */
    @DeleteMapping("/remove/{id}")
    public Result<?> remove(@PathVariable Long id) {
        shelfMapper.deleteById(id);
        return Result.success();
    }
    /**
     * 获取单本书的书架详情 (用于阅读页初始化)
     */
    @GetMapping("/detail")
    public Result<UserBookshelf> getShelfDetail(@RequestParam Long userId, @RequestParam Long bookId) {
        QueryWrapper<UserBookshelf> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("book_id", bookId);
        UserBookshelf shelf = shelfMapper.selectOne(query);
        return Result.success(shelf);
    }
    /**
     * 更新阅读进度 (支持章节)
     */
    @PostMapping("/updateProgress")
    public Result<?> updateProgress(@RequestBody UserBookshelf shelf) {
        QueryWrapper<UserBookshelf> query = new QueryWrapper<>();
        query.eq("user_id", shelf.getUserId()).eq("book_id", shelf.getBookId());

        UserBookshelf exist = shelfMapper.selectOne(query);
        if (exist != null) {
            // 更新段落行数
            exist.setProgressIndex(shelf.getProgressIndex());
            // 更新章节索引 (前端传过来)
            if (shelf.getCurrentChapterIndex() != null) {
                // 注意：这里需要在 UserBookshelf 实体类里加 private Integer currentChapterIndex;
                exist.setCurrentChapterIndex(shelf.getCurrentChapterIndex());
            }
            exist.setLastReadTime(LocalDateTime.now());
            shelfMapper.updateById(exist);
            return Result.success();
        }
        return Result.error("404", "书架中找不到该书");
    }
    @DeleteMapping("/removeByBook")
    public Result<?> removeByBook(@RequestParam Long userId, @RequestParam Long bookId) {
        QueryWrapper<UserBookshelf> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("book_id", bookId);
        shelfMapper.delete(query);
        return Result.success();
    }
}