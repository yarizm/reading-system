package com.example.reading.controller;

import com.example.reading.service.AuthContextService;
import com.example.reading.service.GlobalContextService;
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

@RestController
@RequestMapping("/guide")
@CrossOrigin
public class GuideController {

    @Autowired
    private AuthContextService authContextService;

    @Autowired
    private GlobalContextService globalContextService;

    @Value("${dify.guide.api-url}")
    private String difyBaseUrl;

    @Value("${dify.guide.api-key}")
    private String difyApiKey;

    private final WebClient webClient;

    public GuideController(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    @PostConstruct
    private void normalizeUrl() {
        this.difyBaseUrl = DifyUrlUtils.trimChatMessagesSuffix(difyBaseUrl);
    }

    public static class GuideRequest {
        public String query;
        public String conversationId;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody GuideRequest request, HttpServletRequest httpRequest) {
        Long currentUserId = authContextService.currentUserId(httpRequest);
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        
        SseEmitter emitter = new SseEmitter(300000L);

        Map<String, Object> payload = new HashMap<>();

        // 构建全局上下文
        Map<String, Object> globalContext = globalContextService.buildGlobalContext(currentUserId);
        
        Map<String, String> inputs = new HashMap<>();
        inputs.put("books_reading_count", String.valueOf(globalContext.get("books_reading_count")));
        inputs.put("books_finished_count", String.valueOf(globalContext.get("books_finished_count")));
        inputs.put("total_notes_count", String.valueOf(globalContext.get("total_notes_count")));
        inputs.put("last_read_book_title", String.valueOf(globalContext.getOrDefault("last_read_book_title", "无")));
        inputs.put("system_features", String.valueOf(globalContext.get("system_features")));

        payload.put("inputs", inputs);
        payload.put("response_mode", "streaming");
        payload.put("user", "user-" + currentUserId);
        payload.put("query", request.query);

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
