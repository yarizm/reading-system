package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysNote;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.ISysBookService;
import com.example.reading.service.ISysNoteService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
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

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SocialAiController(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    @PostMapping("/draft-review")
    public Mono<Map<String, Object>> draftReview(@RequestBody Map<String, Object> req, HttpServletRequest httpRequest) {
        Long currentUserId = authContextService.currentUserId(httpRequest);
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        Long bookId = req.containsKey("bookId") ? Long.valueOf(req.get("bookId").toString()) : null;
        if (bookId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bookId is required");
        }

        SysBook book = sysBookService.getById(bookId);
        
        // Fetch user's notes for this book to generate a personalized review
        QueryWrapper<SysNote> noteQuery = new QueryWrapper<>();
        noteQuery.eq("user_id", currentUserId).eq("book_id", bookId);
        List<SysNote> notes = sysNoteService.list(noteQuery);
        String notesContext = notes.stream().map(SysNote::getContent).collect(Collectors.joining(" | "));

        Map<String, Object> payload = new HashMap<>();
        Map<String, String> inputs = new HashMap<>();
        inputs.put("task_type", "book_review");
        inputs.put("book_info", book != null ? book.getTitle() + " - " + book.getAuthor() : "未知书籍");
        inputs.put("context", notesContext);
        
        payload.put("inputs", inputs);
        payload.put("response_mode", "blocking");
        payload.put("user", "user-" + currentUserId);

        return callDify(payload);
    }

    @PostMapping("/draft-share")
    public Mono<Map<String, Object>> draftShare(@RequestBody Map<String, Object> req, HttpServletRequest httpRequest) {
        Long currentUserId = authContextService.currentUserId(httpRequest);
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        String type = (String) req.get("type"); // book or paragraph
        String content = (String) req.get("content");
        String bookTitle = (String) req.get("bookTitle");

        Map<String, Object> payload = new HashMap<>();
        Map<String, String> inputs = new HashMap<>();
        inputs.put("task_type", "share_message");
        inputs.put("book_info", bookTitle);
        inputs.put("context", "Share type: " + type + ", Content: " + content);
        
        payload.put("inputs", inputs);
        payload.put("response_mode", "blocking");
        payload.put("user", "user-" + currentUserId);

        return callDify(payload);
    }

    private Mono<Map<String, Object>> callDify(Map<String, Object> payload) {
        return webClient.post()
                .uri(difyWorkflowUrl + "/workflows/run")
                .header("Authorization", "Bearer " + difyApiKey)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Map<String, Object> data = (Map<String, Object>) response.get("data");
                    if (data != null) {
                        Map<String, Object> outputs = (Map<String, Object>) data.get("outputs");
                        if (outputs != null && outputs.containsKey("result")) {
                            String resultText = (String) outputs.get("result");
                            Map<String, Object> finalRes = new HashMap<>();
                            finalRes.put("result", resultText);
                            return finalRes;
                        }
                        // Fallback if the workflow output variable is named 'text' or something else
                        if (outputs != null && !outputs.isEmpty()) {
                            String resultText = outputs.values().iterator().next().toString();
                            Map<String, Object> finalRes = new HashMap<>();
                            finalRes.put("result", resultText);
                            return finalRes;
                        }
                    }
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Dify 返回格式不正确");
                });
    }
}
