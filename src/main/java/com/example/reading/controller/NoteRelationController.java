package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.NoteRelation;
import com.example.reading.entity.SysNote;
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
    private NoteTagViewService noteTagViewService;

    @Autowired
    private AuthContextService authContextService;

    @PostMapping("/create")
    public Result<?> create(@RequestBody Map<String, Long> body, HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");

        if (body == null) return Result.error("400", "参数不完整");
        Long noteId1 = body.get("noteId1");
        Long noteId2 = body.get("noteId2");
        if (noteId1 == null || noteId2 == null) return Result.error("400", "参数不完整");
        if (noteId1.equals(noteId2)) return Result.error("400", "不能关联自身");

        // 校验笔记归属
        SysNote n1 = sysNoteService.getById(noteId1);
        SysNote n2 = sysNoteService.getById(noteId2);
        if (n1 == null || n2 == null) return Result.error("404", "笔记不存在");
        if (!isOwnedBy(n1, userId) || !isOwnedBy(n2, userId)) {
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
        SysNote n2 = sysNoteService.getById(relation.getNoteId2());
        if (!isOwnedBy(n1, userId) || !isOwnedBy(n2, userId)) {
            return Result.error("403", "Forbidden");
        }

        noteRelationService.removeById(id);
        return Result.success();
    }

    @GetMapping("/list/{noteId}")
    public Result<List<Map<String, Object>>> list(@PathVariable Long noteId, HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");

        SysNote sourceNote = sysNoteService.getById(noteId);
        if (!isOwnedBy(sourceNote, userId)) {
            return Result.error("403", "Forbidden");
        }

        List<NoteRelation> relations = noteRelationService.getRelationsByNoteId(noteId);
        Set<Long> relatedNoteIds = new LinkedHashSet<>();
        for (NoteRelation r : relations) {
            Long relatedNoteId = relatedNoteId(r, noteId);
            if (relatedNoteId != null) {
                relatedNoteIds.add(relatedNoteId);
            }
        }

        Map<Long, SysNote> relatedNoteMap = new HashMap<>();
        if (!relatedNoteIds.isEmpty()) {
            for (SysNote note : sysNoteService.listByIds(relatedNoteIds)) {
                relatedNoteMap.put(note.getId(), note);
            }
        }

        Map<Long, List<Map<String, Object>>> tagInfoByNoteId = noteTagViewService.listTagInfoByNoteIds(relatedNoteIds);

        List<Map<String, Object>> result = new ArrayList<>();

        for (NoteRelation r : relations) {
            Long relatedNoteId = relatedNoteId(r, noteId);
            SysNote relatedNote = relatedNoteMap.get(relatedNoteId);
            if (relatedNote == null) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("relationId", r.getId());

            Map<String, Object> noteInfo = new HashMap<>();
            noteInfo.put("id", relatedNote.getId());
            noteInfo.put("selectedText", relatedNote.getSelectedText());
            noteInfo.put("content", relatedNote.getContent());
            noteInfo.put("createTime", relatedNote.getCreateTime());

            noteInfo.put("tags", tagInfoByNoteId.getOrDefault(relatedNoteId, List.of()));

            item.put("relatedNote", noteInfo);
            result.add(item);
        }
        return Result.success(result);
    }

    private boolean isOwnedBy(SysNote note, Long userId) {
        return note != null && note.getUserId() != null && note.getUserId().equals(userId);
    }

    private Long relatedNoteId(NoteRelation relation, Long sourceNoteId) {
        if (relation == null || sourceNoteId == null) return null;
        if (Objects.equals(relation.getNoteId1(), sourceNoteId)) return relation.getNoteId2();
        if (Objects.equals(relation.getNoteId2(), sourceNoteId)) return relation.getNoteId1();
        return null;
    }
}
