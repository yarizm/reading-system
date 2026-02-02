package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.SysNote;
import com.example.reading.service.ISysNoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/sysNote")
public class SysNoteController {

    @Autowired
    private ISysNoteService sysNoteService;

    /**
     * 新增笔记
     */
    @PostMapping("/add")
    public Result<?> add(@RequestBody SysNote note) {
        note.setCreateTime(LocalDateTime.now());
        sysNoteService.save(note);
        return Result.success();
    }

    /**
     * 获取某本书下的所有笔记
     */
    @GetMapping("/list/{bookId}")
    public Result<List<SysNote>> list(@PathVariable Long bookId) {
        // 实际场景还需要过滤当前 userId，这里简化演示
        QueryWrapper<SysNote> query = new QueryWrapper<>();
        query.eq("book_id", bookId);
        query.orderByDesc("create_time");
        return Result.success(sysNoteService.list(query));
    }

    /**
     * 删除笔记
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        sysNoteService.removeById(id);
        return Result.success();
    }
}