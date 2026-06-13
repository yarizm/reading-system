package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.*;
import com.example.reading.service.*;
import io.github.guoshiqiufeng.dify.chat.DifyChat;
import io.github.guoshiqiufeng.dify.chat.dto.request.ChatMessageSendRequest;
import io.github.guoshiqiufeng.dify.chat.dto.response.ChatMessageSendResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/review")
public class ReviewController {

    @Autowired
    private INoteReviewService noteReviewService;

    @Autowired
    private ISysNoteService sysNoteService;

    @Autowired
    private ISysBookService sysBookService;

    @Autowired
    private IAiGeneratedContentService aiGeneratedContentService;

    @Autowired
    private AuthContextService authContextService;

    @Autowired(required = false)
    private DifyChat difyChat;

    @Value("${dify.reading.api-key:}")
    private String readingApiKey;

    @GetMapping("/today")
    public Result<Map<String, Object>> today(HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");

        List<NoteReview> reviews = noteReviewService.getTodayReviews(userId);
        List<Long> noteIds = reviews.stream().map(NoteReview::getNoteId).toList();

        Map<Long, SysNote> noteMap = new HashMap<>();
        if (!noteIds.isEmpty()) {
            for (SysNote note : sysNoteService.listByIds(noteIds)) {
                noteMap.put(note.getId(), note);
            }
        }

        Set<Long> bookIds = new HashSet<>();
        for (SysNote note : noteMap.values()) {
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

        List<Map<String, Object>> reviewList = new ArrayList<>();

        for (NoteReview r : reviews) {
            SysNote note = noteMap.get(r.getNoteId());
            if (note == null) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("reviewId", r.getId());

            Map<String, Object> noteInfo = new HashMap<>();
            noteInfo.put("id", note.getId());
            noteInfo.put("selectedText", note.getSelectedText());
            noteInfo.put("content", note.getContent());
            noteInfo.put("bookId", note.getBookId());

            SysBook book = bookMap.get(note.getBookId());
            if (book != null) noteInfo.put("bookTitle", book.getTitle());

            item.put("note", noteInfo);
            item.put("intervalDays", r.getIntervalDays());
            item.put("repetitions", r.getRepetitions());
            reviewList.add(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("total", reviewList.size());
        data.put("reviews", reviewList);

        return Result.success(data);
    }

    @PostMapping("/rate")
    public Result<Map<String, Object>> rate(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");
        if (body == null || body.get("noteId") == null || body.get("score") == null) {
            return Result.error("400", "参数不完整");
        }

        Long noteId;
        int score;
        try {
            noteId = Long.valueOf(body.get("noteId").toString());
            score = Integer.parseInt(body.get("score").toString());
        } catch (NumberFormatException e) {
            return Result.error("400", "参数格式错误");
        }

        if (score != 0 && score != 3 && score != 5) {
            return Result.error("400", "score 必须为 0、3 或 5");
        }

        SysNote note = sysNoteService.getById(noteId);
        if (!isOwnedNote(note, userId)) {
            return Result.error("403", "Forbidden");
        }

        Map<String, Object> result = noteReviewService.rate(userId, noteId, score);
        return Result.success(result);
    }

    @PostMapping("/add")
    public Result<String> add(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");
        if (body == null || body.get("noteId") == null) return Result.error("400", "noteId 不能为空");

        Long noteId;
        try {
            noteId = Long.valueOf(body.get("noteId").toString());
        } catch (NumberFormatException e) {
            return Result.error("400", "noteId 格式错误");
        }

        SysNote note = sysNoteService.getById(noteId);
        if (!isOwnedNote(note, userId)) {
            return Result.error("403", "Forbidden");
        }

        noteReviewService.autoAddToReview(userId, noteId);
        return Result.success("已加入回顾");
    }

    @DeleteMapping("/remove/{noteId}")
    public Result<String> remove(@PathVariable Long noteId, HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");

        SysNote note = sysNoteService.getById(noteId);
        if (!isOwnedNote(note, userId)) {
            return Result.error("403", "Forbidden");
        }

        noteReviewService.removeFromReview(userId, noteId);
        return Result.success("已取消回顾");
    }

    @GetMapping("/reviewed-note-ids")
    public Result<Set<Long>> reviewedNoteIds(HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");

        Set<Long> ids = noteReviewService.getReviewedNoteIds(userId);
        return Result.success(ids);
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");

        Map<String, Object> stats = noteReviewService.getStats(userId);

        QueryWrapper<SysNote> noteQuery = new QueryWrapper<>();
        noteQuery.eq("user_id", userId);
        stats.put("totalNotes", sysNoteService.count(noteQuery));

        return Result.success(stats);
    }



    @PostMapping("/summary/{bookId}")
    public Result<?> summary(@PathVariable Long bookId, HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");

        if (difyChat == null || readingApiKey == null || readingApiKey.isEmpty()) {
            return Result.error("500", "AI 服务未配置");
        }

        QueryWrapper<SysNote> noteQuery = new QueryWrapper<>();
        noteQuery.eq("user_id", userId).eq("book_id", bookId).orderByDesc("create_time");
        List<SysNote> notes = sysNoteService.list(noteQuery);

        if (notes.isEmpty()) return Result.error("400", "该书暂无笔记");

        // 限制最多 50 条笔记，防止超出 Dify 上下文窗口
        if (notes.size() > 50) notes = notes.subList(0, 50);

        StringBuilder sb = new StringBuilder();
        SysBook book = sysBookService.getById(bookId);
        String bookTitle = book != null ? book.getTitle() : "未知书籍";
        sb.append("书名：").append(bookTitle).append("\n\n");
        sb.append("以下是这本书的笔记：\n\n");
        for (int i = 0; i < notes.size(); i++) {
            SysNote n = notes.get(i);
            sb.append("笔记 ").append(i + 1).append("：\n");
            if (n.getSelectedText() != null) sb.append("选文：").append(n.getSelectedText()).append("\n");
            sb.append("笔记：").append(n.getContent()).append("\n\n");
        }
        sb.append("请对以上笔记进行总结，提炼核心观点、关键启发和待深入问题。");

        ChatMessageSendRequest chatRequest = new ChatMessageSendRequest();
        chatRequest.setApiKey(readingApiKey);
        chatRequest.setUserId("review-summary-" + userId);
        chatRequest.setContent(sb.toString());

        ChatMessageSendResponse response = difyChat.send(chatRequest);
        String summaryResult = response != null && response.getAnswer() != null
                ? response.getAnswer().toString()
                : "摘要生成失败";

        AiGeneratedContent content = new AiGeneratedContent();
        content.setUserId(userId);
        content.setContentType("book_review_summary");
        content.setReferenceType("book");
        content.setReferenceId(bookId);
        content.setTitle(bookTitle + " - 笔记回顾摘要");
        content.setContent(summaryResult);
        content.setCreateTime(java.time.LocalDateTime.now());
        aiGeneratedContentService.save(content);

        Map<String, Object> result = new HashMap<>();
        result.put("id", content.getId());
        result.put("content", summaryResult);
        result.put("createTime", content.getCreateTime());
        return Result.success(result);
    }

    @GetMapping("/summary/history")
    public Result<List<Map<String, Object>>> summaryHistory(HttpServletRequest request) {
        Long userId = authContextService.currentUserId(request);
        if (userId == null) return Result.error("403", "Forbidden");

        QueryWrapper<AiGeneratedContent> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("content_type", "book_review_summary").orderByDesc("create_time");
        List<AiGeneratedContent> list = aiGeneratedContentService.list(query);

        Set<Long> bookIds = list.stream()
                .map(AiGeneratedContent::getReferenceId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, SysBook> bookMap = new HashMap<>();
        if (!bookIds.isEmpty()) {
            for (SysBook book : sysBookService.listByIds(bookIds)) {
                bookMap.put(book.getId(), book);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (AiGeneratedContent c : list) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", c.getId());
            item.put("bookId", c.getReferenceId());
            SysBook b = bookMap.get(c.getReferenceId());
            if (b != null) item.put("bookTitle", b.getTitle());
            item.put("content", c.getContent());
            item.put("createTime", c.getCreateTime());
            result.add(item);
        }
        return Result.success(result);
    }

    private boolean isOwnedNote(SysNote note, Long userId) {
        return note != null && userId != null && userId.equals(note.getUserId());
    }
}
