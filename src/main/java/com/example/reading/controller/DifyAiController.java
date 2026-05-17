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

import java.util.HashMap;
import java.util.Map;

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

    @Value("${dify.reading.api-url}")
    private String difyApiUrl;

    @Value("${dify.reading.api-key}")
    private String difyApiKey;

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
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟，足够 RAG 检索 + LLM 生成

        Map<String, Object> payload = new HashMap<>();

        Map<String, String> inputs = new HashMap<>();
        inputs.put("selected_text", request.text);
        inputs.put("custom_mode", request.mode);
        inputs.put("book_name", request.bookName != null ? request.bookName : "未知书籍");
        if (request.bookId != null) {
            inputs.put("book_id", String.valueOf(request.bookId));
        }
        payload.put("inputs", inputs);
        payload.put("response_mode", "streaming");
        payload.put("user", "user-" + currentUserId);

        // 组装 query：绑定书名以提高 RAG 检索命中率
        String queryText = request.mode;
        if (request.bookName != null) {
            queryText = "关于《" + request.bookName + "》：" + queryText;
        }
        payload.put("query", queryText);

        if (request.conversationId != null && !request.conversationId.isEmpty()) {
            payload.put("conversation_id", request.conversationId);
        }

        // 自动适配 Dify 聊天助手接口路径
        String realDifyUrl = difyApiUrl;
        if (realDifyUrl.endsWith("/workflows/run")) {
            realDifyUrl = realDifyUrl.replace("/workflows/run", "/chat-messages");
        }

        final boolean[] hasContent = {false};

        WebClient.create().post()
                .uri(realDifyUrl)
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
                            // 已收到内容后上游断开 → 正常结束，否则转发错误
                            if (hasContent[0]) {
                                emitter.complete();
                            } else {
                                emitter.completeWithError(error);
                            }
                        },
                        emitter::complete
                );

        return emitter;
    }
}
