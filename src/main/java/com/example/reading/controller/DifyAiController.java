package com.example.reading.controller;

import com.example.reading.service.AuthContextService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.reading.utils.DifyUrlUtils;
import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dify AI 阅读助手控制器
 * 作为后端与 Dify 平台的 SSE 代理，将前端选中的文本和分析模式转发给 Dify 聊天助手，
 * 并以流式方式将 AI 输出实时推送回前端。
 */
@RestController
@RequestMapping("/difyreading")
@CrossOrigin
public class DifyAiController {

    @Autowired
    private AuthContextService authContextService;

    @Autowired
    private com.example.reading.service.ReadingContextService readingContextService;

    @Value("${dify.reading.api-url}")
    private String difyBaseUrl;

    @Value("${dify.reading.api-key}")
    private String difyApiKey;

    private final WebClient webClient;

    public DifyAiController(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    @PostConstruct
    private void normalizeUrl() {
        this.difyBaseUrl = DifyUrlUtils.trimChatMessagesSuffix(difyBaseUrl);
    }

    /** Dify 分析请求体 */
    public static class AskRequest {
        public String text;
        public String mode;
        public String conversationId;
        public String bookName;
        public Long bookId;
    }

    /** 将选中文本发送给 Dify 进行流式分析 */
    @PostMapping(value = "/analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeText(@RequestBody AskRequest request,
                                  HttpServletRequest httpRequest) {
        Long currentUserId = authContextService.currentUserId(httpRequest);
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟

        Map<String, Object> payload = new HashMap<>();

        Map<String, String> inputs = new HashMap<>();
        inputs.put("selected_text", request.text);
        inputs.put("custom_mode", request.mode);
        inputs.put("book_name", request.bookName != null ? request.bookName : "未知书籍");
        if (request.bookId != null) {
            inputs.put("book_id", String.valueOf(request.bookId));
            
            // 注入阅读上下文
            Map<String, Object> readingContext = readingContextService.buildReadingContext(currentUserId, request.bookId);
            inputs.put("reading_progress", String.valueOf(readingContext.getOrDefault("reading_progress", "")));
            inputs.put("recent_notes", String.valueOf(readingContext.getOrDefault("recent_notes", "")));
        }
        payload.put("inputs", inputs);
        payload.put("response_mode", "streaming");
        payload.put("user", "user-" + currentUserId);

        String queryText = request.mode;
        if (request.bookName != null) {
            queryText = "关于《" + request.bookName + "》：" + queryText;
        }
        payload.put("query", queryText);

        if (request.conversationId != null && !request.conversationId.isEmpty()) {
            payload.put("conversation_id", request.conversationId);
        }

        final boolean[] hasContent = {false};
        final AtomicReference<reactor.core.Disposable> subscriptionRef = new AtomicReference<>();

        emitter.onTimeout(() -> {
            reactor.core.Disposable sub = subscriptionRef.getAndSet(null);
            if (sub != null && !sub.isDisposed()) {
                sub.dispose();
            }
        });
        emitter.onCompletion(() -> {
            reactor.core.Disposable sub = subscriptionRef.getAndSet(null);
            if (sub != null && !sub.isDisposed()) {
                sub.dispose();
            }
        });

        subscriptionRef.set(webClient.post()
                .uri(difyBaseUrl + "/chat-messages")
                .header("Authorization", "Bearer " + difyApiKey)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(String.class)
                .subscribe(
                        chunk -> {
                            try {
                                hasContent[0] = true;
                                emitter.send(chunk);
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            if (hasContent[0]) {
                                emitter.complete();
                            } else {
                                emitter.completeWithError(error);
                            }
                        },
                        emitter::complete
                ));

        return emitter;
    }
}
