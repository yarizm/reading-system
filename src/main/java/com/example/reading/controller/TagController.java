package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.SysNote;
import com.example.reading.entity.SysTag;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.INoteTagService;
import com.example.reading.service.ISysNoteService;
import com.example.reading.service.ISysTagService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tag")
public class TagController {

    @Autowired
    private ISysTagService tagService;

    @Autowired
    private INoteTagService noteTagService;

    @Autowired
    private ISysNoteService sysNoteService;

    @Autowired
    private AuthContextService authContextService;

    @GetMapping("/list")
    public Result<List<SysTag>> list(HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");
        tagService.ensureSystemTags(userId);
        return Result.success(tagService.getUserTags(userId));
    }

    @PostMapping("/create")
    public Result<?> create(@RequestBody SysTag tag, HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");
        if (tag.getName() == null || tag.getName().trim().isEmpty()) {
            return Result.error("400", "标签名不能为空");
        }
        if (tag.getName().length() > 50) {
            return Result.error("400", "标签名不能超过50个字符");
        }
        tag.setUserId(userId);
        tag.setIsSystem(0);
        tagService.save(tag);
        return Result.success(tag);
    }

    @PutMapping("/update/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody SysTag tag, HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");
        SysTag existing = tagService.getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Result.error("403", "Forbidden");
        }
        if (existing.getIsSystem() == 1 && tag.getName() != null && !tag.getName().equals(existing.getName())) {
            return Result.error("400", "系统标签不能修改名称");
        }
        if (tag.getName() != null) existing.setName(tag.getName());
        if (tag.getColor() != null) existing.setColor(tag.getColor());
        tagService.updateById(existing);
        return Result.success(existing);
    }

    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");
        SysTag existing = tagService.getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Result.error("403", "Forbidden");
        }
        if (existing.getIsSystem() == 1) {
            return Result.error("400", "系统标签不可删除");
        }
        tagService.removeById(id);
        return Result.success();
    }

    @PostMapping("/bind")
    public Result<?> bind(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");
        if (body.get("noteId") == null || body.get("tagIds") == null) {
            return Result.error("400", "参数不完整");
        }
        Long noteId = Long.valueOf(body.get("noteId").toString());
        SysNote note = sysNoteService.getById(noteId);
        if (note == null || !note.getUserId().equals(userId)) {
            return Result.error("403", "无权操作此笔记");
        }
        @SuppressWarnings("unchecked")
        List<Long> tagIds = ((List<Number>) body.get("tagIds")).stream().map(Number::longValue).toList();
        noteTagService.bindTags(noteId, tagIds);
        return Result.success();
    }

    @DeleteMapping("/unbind")
    public Result<?> unbind(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");
        if (body.get("noteId") == null || body.get("tagIds") == null) {
            return Result.error("400", "参数不完整");
        }
        Long noteId = Long.valueOf(body.get("noteId").toString());
        SysNote note = sysNoteService.getById(noteId);
        if (note == null || !note.getUserId().equals(userId)) {
            return Result.error("403", "无权操作此笔记");
        }
        @SuppressWarnings("unchecked")
        List<Long> tagIds = ((List<Number>) body.get("tagIds")).stream().map(Number::longValue).toList();
        noteTagService.unbindTags(noteId, tagIds);
        return Result.success();
    }
}
