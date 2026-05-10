package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.SysNote;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.ISysNoteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/sysNote")
public class SysNoteController {

    @Autowired
    private ISysNoteService sysNoteService;

    @Autowired
    private AuthContextService authContextService;

    @PostMapping("/add")
    public Result<?> add(@RequestBody SysNote note, HttpServletRequest request) {
        Long currentUserId = authContextService.currentUserId(request);
        if (currentUserId == null || note.getBookId() == null) {
            return Result.error("403", "Forbidden");
        }
        note.setUserId(currentUserId);
        note.setCreateTime(LocalDateTime.now());
        sysNoteService.save(note);
        return Result.success();
    }

    @GetMapping("/list/{bookId}")
    public Result<List<SysNote>> list(@PathVariable Long bookId,
                                      @RequestParam(required = false) Long userId,
                                      HttpServletRequest request) {
        Long currentUserId = authContextService.currentUserId(request);
        if (currentUserId == null) {
            return Result.error("403", "Forbidden");
        }
        QueryWrapper<SysNote> query = new QueryWrapper<>();
        query.eq("book_id", bookId).eq("user_id", currentUserId).orderByDesc("create_time");
        return Result.success(sysNoteService.list(query));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request) {
        SysNote note = sysNoteService.getById(id);
        if (note == null) return Result.success();
        if (!authContextService.isSelf(note.getUserId(), request)) {
            return Result.error("403", "Forbidden");
        }
        sysNoteService.removeById(id);
        return Result.success();
    }
}
