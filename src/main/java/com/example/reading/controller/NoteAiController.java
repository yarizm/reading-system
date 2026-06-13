package com.example.reading.controller;

import com.example.reading.entity.AiGeneratedContent;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.DifyWorkflowClient;
import com.example.reading.service.IAiGeneratedContentService;
import com.example.reading.service.ISysBookService;
import com.example.reading.service.NoteAiService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/note/ai")
@CrossOrigin
public class NoteAiController {

    @Autowired
    private AuthContextService authContextService;

    @Autowired
    private NoteAiService noteAiService;

    @Autowired
    private IAiGeneratedContentService aiGeneratedContentService;

    @Autowired
    private ISysBookService sysBookService;

    private final DifyWorkflowClient difyWorkflowClient;
    private final String difyWorkflowUrl;
    private final String difyApiKey;

    public NoteAiController(DifyWorkflowClient difyWorkflowClient,
                             @Value("${dify.note.api-url}") String url,
                             @Value("${dify.note.api-key}") String apiKey) {
        this.difyWorkflowClient = difyWorkflowClient;
        this.difyWorkflowUrl = url;
        this.difyApiKey = apiKey;
    }

    public static class NoteAiRequest {
        public String action; // "enhance" | "summarize" | "quiz"
        public String singleNoteContent; // 用于润色/扩展单个笔记
        public String title;
    }

    @PostMapping("/run/{bookId}")
    public Mono<Map<String, Object>> runNoteWorkflow(@PathVariable Long bookId, @RequestBody NoteAiRequest request, HttpServletRequest httpRequest) {
        Long currentUserId = authContextService.currentUserId(httpRequest);
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (request == null || request.action == null || request.action.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action is required");
        }
        if (!isSupportedAction(request.action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported action");
        }
        if ("enhance".equals(request.action)
                && (request.singleNoteContent == null || request.singleNoteContent.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "singleNoteContent is required");
        }

        Map<String, String> inputs = new HashMap<>();
        inputs.put("task_type", "note");
        inputs.put("action", request.action);

        if ("summarize".equals(request.action) || "quiz".equals(request.action)) {
            String allNotes = noteAiService.buildBookNotesContext(currentUserId, bookId);
            inputs.put("notes_context", allNotes);
        } else if ("enhance".equals(request.action)) {
            inputs.put("notes_context", request.singleNoteContent);
        }

        // 获取书籍信息
        String bookInfo = "未知书籍";
        try {
            var book = sysBookService.getById(bookId);
            if (book != null) bookInfo = book.getTitle() + " - " + (book.getAuthor() != null ? book.getAuthor() : "");
        } catch (Exception ignored) {}
        inputs.put("book_info", bookInfo);
        inputs.put("context", request.title != null ? request.title : "");

        return difyWorkflowClient.runBlocking(difyWorkflowUrl, difyApiKey, inputs, "user-" + currentUserId)
                .map(outputs -> {
                    Object result = outputs.get("result");
                    if (result != null) {
                        String resultText = String.valueOf(result);

                        AiGeneratedContent content = new AiGeneratedContent();
                        content.setUserId(currentUserId);
                        content.setContentType("note_" + request.action);
                        content.setReferenceType("book");
                        content.setReferenceId(bookId);
                        content.setTitle(request.title != null ? request.title : "AI Generated " + request.action);
                        content.setContent(resultText);
                        content.setCreateTime(java.time.LocalDateTime.now());
                        aiGeneratedContentService.save(content);

                        Map<String, Object> finalRes = new HashMap<>();
                        finalRes.put("id", content.getId());
                        finalRes.put("result", resultText);
                        return finalRes;
                    }
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Dify 返回格式不正确");
                });
    }

    @GetMapping("/result/{id}")
    public Map<String, Object> getResult(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long currentUserId = authContextService.currentUserId(httpRequest);
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        
        AiGeneratedContent content = aiGeneratedContentService.getById(id);
        if (content == null || !currentUserId.equals(content.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "内容不存在或无权限访问");
        }
        
        Map<String, Object> res = new HashMap<>();
        res.put("id", content.getId());
        res.put("title", content.getTitle());
        res.put("content", content.getContent());
        res.put("contentType", content.getContentType());
        res.put("createTime", content.getCreateTime());
        return res;
    }

    private boolean isSupportedAction(String action) {
        return "enhance".equals(action) || "summarize".equals(action) || "quiz".equals(action);
    }
}
