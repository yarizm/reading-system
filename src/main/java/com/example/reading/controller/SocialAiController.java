package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysNote;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.DifyWorkflowClient;
import com.example.reading.service.ISysBookService;
import com.example.reading.service.ISysNoteService;
import com.example.reading.utils.MapParamUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/social/ai")
@CrossOrigin
public class SocialAiController {

    @Autowired
    private AuthContextService authContextService;

    @Autowired
    private ISysNoteService sysNoteService;

    @Autowired
    private ISysBookService sysBookService;

    @Value("${dify.note.api-url}")
    private String difyWorkflowUrl;

    @Value("${dify.note.api-key}")
    private String difyApiKey;

    private final DifyWorkflowClient difyWorkflowClient;

    public SocialAiController(DifyWorkflowClient difyWorkflowClient) {
        this.difyWorkflowClient = difyWorkflowClient;
    }

    @PostMapping("/draft-review")
    public Mono<Map<String, Object>> draftReview(@RequestBody Map<String, Object> req, HttpServletRequest httpRequest) {
        Long currentUserId = authContextService.currentUserId(httpRequest);
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }

        Long bookId = MapParamUtils.asLong(req, "bookId");
        if (bookId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bookId is required");
        }

        SysBook book = sysBookService.getById(bookId);
        
        // Fetch user's notes for this book to generate a personalized review
        QueryWrapper<SysNote> noteQuery = new QueryWrapper<>();
        noteQuery.eq("user_id", currentUserId).eq("book_id", bookId);
        List<SysNote> notes = sysNoteService.list(noteQuery);
        String notesContext = notes.stream()
                .map(SysNote::getContent)
                .filter(c -> c != null && !c.trim().isEmpty())
                .collect(Collectors.joining(" | "));

        Map<String, String> inputs = new HashMap<>();
        inputs.put("task_type", "book_review");
        inputs.put("book_info", book != null ? book.getTitle() + " - " + book.getAuthor() : "未知书籍");
        inputs.put("context", notesContext);

        return callDify(inputs, currentUserId);
    }

    @PostMapping("/draft-share")
    public Mono<Map<String, Object>> draftShare(@RequestBody Map<String, Object> req, HttpServletRequest httpRequest) {
        Long currentUserId = authContextService.currentUserId(httpRequest);
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }

        String type = req.get("type") != null ? req.get("type").toString() : "book";
        String content = req.get("content") != null ? req.get("content").toString() : null;
        if (content == null || content.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        }
        String bookTitle = req.get("bookTitle") != null ? req.get("bookTitle").toString() : "未知书籍";

        Map<String, String> inputs = new HashMap<>();
        inputs.put("task_type", "share_message");
        inputs.put("book_info", bookTitle);
        inputs.put("context", "Share type: " + type + ", Content: " + content);

        return callDify(inputs, currentUserId);
    }

    private Mono<Map<String, Object>> callDify(Map<String, String> inputs, Long currentUserId) {
        return difyWorkflowClient.runBlocking(difyWorkflowUrl, difyApiKey, inputs, "user-" + currentUserId)
                .map(response -> {
                    Object result = response.get("result");
                    if (result != null) {
                        Map<String, Object> finalRes = new HashMap<>();
                        finalRes.put("result", String.valueOf(result));
                        return finalRes;
                    }
                    // Fallback if the workflow output variable is named 'text' or something else.
                    if (!response.isEmpty()) {
                        Map<String, Object> finalRes = new HashMap<>();
                        finalRes.put("result", String.valueOf(response.values().iterator().next()));
                        return finalRes;
                    }
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Dify 返回格式不正确");
                });
    }
}
