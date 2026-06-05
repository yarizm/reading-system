package com.example.reading.controller;

import com.example.reading.entity.AiGeneratedContent;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.IAiGeneratedContentService;
import com.example.reading.service.NoteAiService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
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

    @Value("${dify.note.api-url}")
    private String difyWorkflowUrl;

    @Value("${dify.note.api-key}")
    private String difyApiKey;

    private final WebClient webClient;

    public NoteAiController(WebClient.Builder builder) {
        this.webClient = builder.build();
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

        Map<String, Object> payload = new HashMap<>();
        Map<String, String> inputs = new HashMap<>();
        inputs.put("action", request.action);
        
        if ("summarize".equals(request.action) || "quiz".equals(request.action)) {
            String allNotes = noteAiService.buildBookNotesContext(currentUserId, bookId);
            inputs.put("notes_context", allNotes);
        } else if ("enhance".equals(request.action)) {
            inputs.put("notes_context", request.singleNoteContent);
        }

        payload.put("inputs", inputs);
        payload.put("response_mode", "blocking");
        payload.put("user", "user-" + currentUserId);

        return webClient.post()
                .uri(difyWorkflowUrl + "/workflows/run")
                .header("Authorization", "Bearer " + difyApiKey)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .publishOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .map(response -> {
                    // 解析 Dify Workflow 返回结果
                    Map<String, Object> data = (Map<String, Object>) response.get("data");
                    if (data != null) {
                        Map<String, Object> outputs = (Map<String, Object>) data.get("outputs");
                        if (outputs != null && outputs.containsKey("result")) {
                            String resultText = (String) outputs.get("result");

                            // 保存到数据库
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
        if (content == null || !content.getUserId().equals(currentUserId)) {
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
}
