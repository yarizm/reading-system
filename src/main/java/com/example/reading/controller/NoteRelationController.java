package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.NoteRelation;
import com.example.reading.entity.SysNote;
import com.example.reading.entity.SysTag;
import com.example.reading.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/noteRelation")
public class NoteRelationController {

    @Autowired
    private INoteRelationService noteRelationService;

    @Autowired
    private ISysNoteService sysNoteService;

    @Autowired
    private INoteTagService noteTagService;

    @Autowired
    private ISysTagService tagService;

    @Autowired
    private AuthContextService authContextService;

    @PostMapping("/create")
    public Result<?> create(@RequestBody Map<String, Long> body, HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");

        Long noteId1 = body.get("noteId1");
        Long noteId2 = body.get("noteId2");
        if (noteId1 == null || noteId2 == null) return Result.error("400", "参数不完整");
        if (noteId1.equals(noteId2)) return Result.error("400", "不能关联自身");

        // 校验笔记归属
        SysNote n1 = sysNoteService.getById(noteId1);
        SysNote n2 = sysNoteService.getById(noteId2);
        if (n1 == null || n2 == null) return Result.error("404", "笔记不存在");
        if (!n1.getUserId().equals(userId) || !n2.getUserId().equals(userId)) {
            return Result.error("403", "Forbidden");
        }

        NoteRelation relation = noteRelationService.createRelation(noteId1, noteId2);
        return Result.success(relation);
    }

    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");

        NoteRelation relation = noteRelationService.getById(id);
        if (relation == null) return Result.success();

        // 校验关联的笔记是否属于当前用户
        SysNote n1 = sysNoteService.getById(relation.getNoteId1());
        if (n1 == null || !n1.getUserId().equals(userId)) return Result.error("403", "Forbidden");

        noteRelationService.removeById(id);
        return Result.success();
    }

    @GetMapping("/list/{noteId}")
    public Result<List<Map<String, Object>>> list(@PathVariable Long noteId, HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");

        SysNote sourceNote = sysNoteService.getById(noteId);
        if (sourceNote == null || !sourceNote.getUserId().equals(userId)) {
            return Result.error("403", "Forbidden");
        }

        List<NoteRelation> relations = noteRelationService.getRelationsByNoteId(noteId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (NoteRelation r : relations) {
            Long relatedNoteId = r.getNoteId1().equals(noteId) ? r.getNoteId2() : r.getNoteId1();
            SysNote relatedNote = sysNoteService.getById(relatedNoteId);
            if (relatedNote == null) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("relationId", r.getId());

            Map<String, Object> noteInfo = new HashMap<>();
            noteInfo.put("id", relatedNote.getId());
            noteInfo.put("selectedText", relatedNote.getSelectedText());
            noteInfo.put("content", relatedNote.getContent());
            noteInfo.put("createTime", relatedNote.getCreateTime());

            // 获取关联笔记的标签
            List<Long> tagIds = noteTagService.getTagIdsByNoteId(relatedNoteId);
            if (!tagIds.isEmpty()) {
                List<SysTag> tags = tagService.listByIds(tagIds);
                noteInfo.put("tags", tags.stream().map(t -> {
                    Map<String, Object> tagMap = new HashMap<>();
                    tagMap.put("id", t.getId());
                    tagMap.put("name", t.getName());
                    tagMap.put("color", t.getColor());
                    return tagMap;
                }).toList());
            } else {
                noteInfo.put("tags", List.of());
            }

            item.put("relatedNote", noteInfo);
            result.add(item);
        }
        return Result.success(result);
    }
}
