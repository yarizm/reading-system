package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.SysNote;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.NoteTag;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.ISysNoteService;
import com.example.reading.service.ISysBookService;
import com.example.reading.service.INoteTagService;
import com.example.reading.service.INoteReviewService;
import com.example.reading.service.NoteEsService;
import com.example.reading.service.NoteTagViewService;
import com.example.reading.utils.PaginationUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/sysNote")
public class SysNoteController {

    @Autowired
    private ISysNoteService sysNoteService;

    @Autowired
    private AuthContextService authContextService;

    @Autowired
    private ISysBookService sysBookService;

    @Autowired
    private INoteTagService noteTagService;

    @Autowired
    private NoteTagViewService noteTagViewService;

    @Autowired
    private NoteEsService noteEsService;

    @Autowired
    private INoteReviewService noteReviewService;

    @PostMapping("/add")
    public Result<?> add(@RequestBody SysNote note, HttpServletRequest request) {
        Long currentUserId = authContextService.currentUserId(request);
        if (currentUserId == null || note == null || note.getBookId() == null) {
            return Result.error("403", "Forbidden");
        }
        if (!authContextService.canViewBook(note.getBookId(), request)) {
            return Result.error("403", "Forbidden");
        }
        note.setUserId(currentUserId);
        note.setCreateTime(LocalDateTime.now());
        sysNoteService.save(note);
        noteEsService.syncNoteToEs(note);
        noteReviewService.autoAddToReview(currentUserId, note.getId());
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
        if (!authContextService.canViewBook(bookId, request)) {
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
        noteEsService.deleteNoteFromEs(id);
        return Result.success();
    }

    @GetMapping("/globalList")
    public Result<Map<String, Object>> globalList(
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) Long bookId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            HttpServletRequest request) {

        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");

        page = PaginationUtils.pageNum(page);
        if (size == null || size < 1 || size > 100) size = 20;

        // 如果有关键词，走 ES 搜索
        if (keyword != null && !keyword.trim().isEmpty()) {
            Map<String, Object> esResult = noteEsService.searchNotes(userId, keyword, tagId, bookId, page, size);
            if (esResult != null) return Result.success(esResult);
        }

        // 否则走 MySQL 查询
        QueryWrapper<SysNote> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String likePattern = "%" + keyword.trim() + "%";
            query.and(q -> q.like("selected_text", likePattern).or().like("content", likePattern));
        }
        if (bookId != null) query.eq("book_id", bookId);
        if (startDate != null) query.ge("create_time", startDate);
        if (endDate != null) query.le("create_time", endDate + " 23:59:59");
        query.orderByDesc("create_time");

        // 按标签筛选
        if (tagId != null) {
            QueryWrapper<NoteTag> noteTagQuery = new QueryWrapper<>();
            noteTagQuery.eq("tag_id", tagId);
            List<Long> noteIds = noteTagService.list(noteTagQuery).stream()
                    .map(NoteTag::getNoteId).toList();
            if (noteIds.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("records", List.of());
                empty.put("total", 0);
                return Result.success(empty);
            }
            query.in("id", noteIds);
        }

        // 分页
        long total = sysNoteService.count(query);
        query.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        List<SysNote> notes = sysNoteService.list(query);

        // 组装返回数据（包含书籍信息和标签）
        Set<Long> bookIds = new HashSet<>();
        List<Long> pageNoteIds = new ArrayList<>();
        for (SysNote note : notes) {
            pageNoteIds.add(note.getId());
            if (note.getBookId() != null) {
                bookIds.add(note.getBookId());
            }
        }

        Map<Long, SysBook> bookMap = new HashMap<>();
        if (!bookIds.isEmpty()) {
            for (SysBook book : sysBookService.listByIds(bookIds)) {
                bookMap.put(book.getId(), book);
            }
        }

        Map<Long, List<Map<String, Object>>> tagInfoByNoteId = noteTagViewService.listTagInfoByNoteIds(pageNoteIds);

        List<Map<String, Object>> records = new ArrayList<>();
        for (SysNote note : notes) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", note.getId());
            item.put("bookId", note.getBookId());
            item.put("selectedText", note.getSelectedText());
            item.put("content", note.getContent());
            item.put("createTime", note.getCreateTime());

            // 书籍信息
            SysBook book = bookMap.get(note.getBookId());
            if (book != null) {
                item.put("bookTitle", book.getTitle());
                item.put("bookAuthor", book.getAuthor());
                item.put("bookCoverUrl", book.getCoverUrl());
            }

            // 标签信息
            item.put("tags", tagInfoByNoteId.getOrDefault(note.getId(), List.of()));

            records.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        return Result.success(result);
    }
}
