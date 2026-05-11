package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysUser;
import com.example.reading.entity.UserBookshelf;
import com.example.reading.mapper.UserBookshelfMapper;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.ISysBookService;
import com.example.reading.service.ISysUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户书架控制器
 * 提供书架的增删查、阅读进度更新等功能。
 */
@RestController
@RequestMapping("/bookshelf")
public class UserBookshelfController {

    @Autowired
    private UserBookshelfMapper shelfMapper;

    @Autowired
    private AuthContextService authContextService;

    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    private ISysBookService sysBookService;

    private Long currentUserId(HttpServletRequest request) {
        return authContextService.currentUserId(request);
    }

    private boolean canViewShelf(Long userId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        if (currentUserId != null && (currentUserId.equals(userId) || authContextService.isAdmin(currentUserId))) {
            return true;
        }
        SysUser user = sysUserService.getById(userId);
        return user != null && (user.getShelfVisible() == null || Integer.valueOf(1).equals(user.getShelfVisible()));
    }

    private boolean canViewFullShelf(Long userId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        return currentUserId != null && (currentUserId.equals(userId) || authContextService.isAdmin(currentUserId));
    }

    private boolean isSelf(Long userId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        return currentUserId != null && currentUserId.equals(userId);
    }

    /** 加入书架（自动去重） */
    @PostMapping("/add")
    public Result<?> add(@RequestBody UserBookshelf shelf, HttpServletRequest request) {
        if (!isSelf(shelf.getUserId(), request)) {
            return Result.error("403", "Forbidden");
        }
        Long currentUserId = currentUserId(request);
        SysBook book = sysBookService.getById(shelf.getBookId());
        if (!authContextService.canAccessBook(book, currentUserId)) {
            return Result.error("403", "Forbidden");
        }
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

    /** 获取用户书架列表（关联书籍详情） */
    @GetMapping("/list/{userId}")
    public Result<List<Map<String, Object>>> list(@PathVariable Long userId,
                                                  HttpServletRequest request) {
        if (!canViewShelf(userId, request)) {
            return Result.error("403", "Forbidden");
        }
        if (canViewFullShelf(userId, request)) {
            return Result.success(shelfMapper.selectMyShelf(userId));
        }
        return Result.success(shelfMapper.selectPublicShelf(userId));
    }

    /** 按书架记录 ID 移出书架 */
    @DeleteMapping("/remove/{id}")
    public Result<?> remove(@PathVariable Long id, HttpServletRequest request) {
        UserBookshelf shelf = shelfMapper.selectById(id);
        if (shelf == null) {
            return Result.error("404", "Not found");
        }
        if (!isSelf(shelf.getUserId(), request)) {
            return Result.error("403", "Forbidden");
        }
        shelfMapper.deleteById(id);
        return Result.success();
    }

    /** 获取单本书的书架详情（用于阅读页初始化进度恢复） */
    @GetMapping("/detail")
    public Result<UserBookshelf> getShelfDetail(@RequestParam Long userId,
                                                @RequestParam Long bookId,
                                                HttpServletRequest request) {
        if (!isSelf(userId, request)) {
            return Result.error("403", "Forbidden");
        }
        QueryWrapper<UserBookshelf> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("book_id", bookId);
        return Result.success(shelfMapper.selectOne(query));
    }

    /** 更新阅读进度（段落行数 + 章节索引） */
    @PostMapping("/updateProgress")
    public Result<?> updateProgress(@RequestBody UserBookshelf shelf, HttpServletRequest request) {
        if (!isSelf(shelf.getUserId(), request)) {
            return Result.error("403", "Forbidden");
        }
        QueryWrapper<UserBookshelf> query = new QueryWrapper<>();
        query.eq("user_id", shelf.getUserId()).eq("book_id", shelf.getBookId());

        UserBookshelf exist = shelfMapper.selectOne(query);
        if (exist != null) {
            exist.setProgressIndex(shelf.getProgressIndex());
            if (shelf.getCurrentChapterIndex() != null) {
                exist.setCurrentChapterIndex(shelf.getCurrentChapterIndex());
            }
            exist.setLastReadTime(LocalDateTime.now());
            shelfMapper.updateById(exist);
            return Result.success();
        }
        return Result.error("404", "书架中找不到该书");
    }

    /** 按用户 ID + 书籍 ID 移出书架 */
    @DeleteMapping("/removeByBook")
    public Result<?> removeByBook(@RequestParam Long userId,
                                  @RequestParam Long bookId,
                                  HttpServletRequest request) {
        if (!isSelf(userId, request)) {
            return Result.error("403", "Forbidden");
        }
        QueryWrapper<UserBookshelf> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("book_id", bookId);
        shelfMapper.delete(query);
        return Result.success();
    }
}
