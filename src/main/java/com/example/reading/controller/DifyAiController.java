package com.example.reading.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/difyreading")
@CrossOrigin // 允许 Vue 前端跨域调用
public class DifyAiController {

    @Value("${dify.api-url}")
    private String difyApiUrl;

    @Value("${dify.api-key}")
    private String difyApiKey;

    // 1. 接收前端 Vue 传来的数据结构
    public static class AskRequest {
        public String text;           // 用户框选的原文
        public String mode;           // 用户选择的模式（例如"鲁迅锐评"）
        public String conversationId; // 🌟 新增：前端传来的会话ID，用于上下文记忆
        public String bookName; // 🌟 新增：当前正在阅读的书名
    }

    // 2. 核心接口：注意 produces 必须是 TEXT_EVENT_STREAM_VALUE (SSE流格式)
    @PostMapping(value = "/analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeText(@RequestBody AskRequest request) {

        // SseEmitter 用于向前端持续推送数据。设置 60 秒超时（大模型生成比较慢）
        SseEmitter emitter = new SseEmitter(60000L);

        // 3. 严格按照 Dify API 文档要求，组装 JSON 报文
        Map<String, Object> payload = new HashMap<>();

        // inputs 里的 key 必须和你 Dify "开始"节点里定义的变量名【一模一样】！
        Map<String, String> inputs = new HashMap<>();
        inputs.put("selected_text", request.text);
        inputs.put("custom_mode", request.mode);

        // 传给 Dify，让 AI 知道现在看的是哪本书
        inputs.put("book_name", request.bookName != null ? request.bookName : "未知书籍");
        payload.put("inputs", inputs);
        payload.put("response_mode", "streaming"); // 🔥 极其重要：告诉 Dify 一字一字吐出来
        payload.put("user", "user-vue-001");       // 随便填个用户标记

        // 🌟 新增 A：Dify 聊天助手(Chatbot)接口强制要求必须传 query。
        // 👇 优化 Query 的组装：把书名和问题绑定，大幅提高 RAG 的检索命中率！
        String queryText = request.mode;
        if (request.bookName != null) {
            queryText = "关于《" + request.bookName + "》：" + queryText;
        }
        payload.put("query", queryText);

        // 🌟 新增 B：如果前端传了会话ID（代表是多轮连续对话），就带上它，Dify 就会自动读取历史记忆
        if (request.conversationId != null && !request.conversationId.isEmpty()) {
            payload.put("conversation_id", request.conversationId);
        }

        // 🌟 新增 C：动态替换 URL
        // 因为工作流的接口是 /workflows/run，而聊天助手的接口是 /chat-messages
        // 如果你在 application.yml 里没改，这里会自动帮你替换
        String realDifyUrl = difyApiUrl;
        if (realDifyUrl.endsWith("/workflows/run")) {
            realDifyUrl = realDifyUrl.replace("/workflows/run", "/chat-messages");
        }

        // 4. 使用 WebClient 发起异步请求到 Dify
        WebClient.create().post()
                .uri(realDifyUrl)
                .header("Authorization", "Bearer " + difyApiKey)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(String.class) // 将 Dify 持续返回的数据转为 Flux 字符串流
                .subscribe(
                        // onNext: Dify 每吐出一个数据块 (chunk)，就立刻通过 emitter 推给前端 Vue
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (Exception e) {
                                emitter.completeWithError(e); // 推送失败则中断
                            }
                        },
                        // onError: Dify 报错或网络异常
                        error -> emitter.completeWithError(error),
                        // onComplete: Dify 说它生成完了
                        () -> emitter.complete()
                );

        return emitter; // 立刻返回 emitter，此时通道建立，等待 Dify 吐字
    }
}