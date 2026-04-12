package com.example.reading.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
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
    }

    /** 将选中文本发送给 Dify 进行流式分析 */
    @PostMapping(value = "/analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeText(@RequestBody AskRequest request) {
        SseEmitter emitter = new SseEmitter(60000L);

        Map<String, Object> payload = new HashMap<>();

        Map<String, String> inputs = new HashMap<>();
        inputs.put("selected_text", request.text);
        inputs.put("custom_mode", request.mode);
        inputs.put("book_name", request.bookName != null ? request.bookName : "未知书籍");
        payload.put("inputs", inputs);
        payload.put("response_mode", "streaming");
        payload.put("user", "user-vue-001");

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

        WebClient.create().post()
                .uri(realDifyUrl)
                .header("Authorization", "Bearer " + difyApiKey)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(String.class)
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );

        return emitter;
    }
}
