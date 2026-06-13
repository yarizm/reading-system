package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.SysNote;
import com.example.reading.entity.SysTag;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.INoteTagService;
import com.example.reading.service.ISysNoteService;
import com.example.reading.service.ISysTagService;
import com.example.reading.utils.MapParamUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
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
        if (tag == null) return Result.error("400", "标签名不能为空");
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
        if (tag == null) return Result.error("400", "参数不完整");
        SysTag existing = tagService.getById(id);
        if (existing == null || !userId.equals(existing.getUserId())) {
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
        if (existing == null || !userId.equals(existing.getUserId())) {
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
        if (body == null || body.get("noteId") == null || body.get("tagIds") == null) {
            return Result.error("400", "参数不完整");
        }
        Long noteId = MapParamUtils.asLong(body, "noteId");
        if (noteId == null) {
            return Result.error("400", "noteId 格式错误");
        }
        SysNote note = sysNoteService.getById(noteId);
        if (note == null || !userId.equals(note.getUserId())) {
            return Result.error("403", "无权操作此笔记");
        }
        List<Long> tagIds;
        try {
            tagIds = parseLongList(body.get("tagIds"));
        } catch (IllegalArgumentException e) {
            return Result.error("400", "tagIds 格式错误");
        }
        noteTagService.bindTags(noteId, tagIds);
        return Result.success();
    }

    @DeleteMapping("/unbind")
    public Result<?> unbind(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");
        if (body == null || body.get("noteId") == null || body.get("tagIds") == null) {
            return Result.error("400", "参数不完整");
        }
        Long noteId = MapParamUtils.asLong(body, "noteId");
        if (noteId == null) {
            return Result.error("400", "noteId 格式错误");
        }
        SysNote note = sysNoteService.getById(noteId);
        if (note == null || !userId.equals(note.getUserId())) {
            return Result.error("403", "无权操作此笔记");
        }
        List<Long> tagIds;
        try {
            tagIds = parseLongList(body.get("tagIds"));
        } catch (IllegalArgumentException e) {
            return Result.error("400", "tagIds 格式错误");
        }
        noteTagService.unbindTags(noteId, tagIds);
        return Result.success();
    }

    private List<Long> parseLongList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            throw new IllegalArgumentException("not a list");
        }

        List<Long> result = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            if (item instanceof Number number) {
                result.add(number.longValue());
                continue;
            }
            if (item instanceof String text) {
                try {
                    result.add(Long.valueOf(text));
                    continue;
                } catch (NumberFormatException ignored) {
                    // Fall through to the shared format error below.
                }
            }
            throw new IllegalArgumentException("invalid id");
        }
        return result;
    }
}
