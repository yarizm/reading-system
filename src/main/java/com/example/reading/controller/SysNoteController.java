package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.SysNote;
import com.example.reading.service.ISysNoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 阅读笔记控制器
 * 提供笔记的新增、按书籍查询及删除功能。
 */
@RestController
@RequestMapping("/sysNote")
public class SysNoteController {

    @Autowired
    private ISysNoteService sysNoteService;

    /** 新增笔记 */
    @PostMapping("/add")
    public Result<?> add(@RequestBody SysNote note) {
        note.setCreateTime(LocalDateTime.now());
        sysNoteService.save(note);
        return Result.success();
    }

    /** 获取指定书籍下的笔记列表（可按用户筛选） */
    @GetMapping("/list/{bookId}")
    public Result<List<SysNote>> list(@PathVariable Long bookId, @RequestParam(required = false) Long userId) {
        QueryWrapper<SysNote> query = new QueryWrapper<>();
        query.eq("book_id", bookId);
        if (userId != null) {
            query.eq("user_id", userId);
        }
        query.orderByDesc("create_time");
        return Result.success(sysNoteService.list(query));
    }

    /** 删除笔记 */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        sysNoteService.removeById(id);
        return Result.success();
    }
}