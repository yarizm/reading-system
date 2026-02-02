package com.example.reading.controller;
import java.util.Collections;
import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import io.reactivex.Flowable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.example.reading.dto.AiRequestDto;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.example.reading.common.Result; // 记得引入Result
import cn.hutool.core.util.IdUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import org.springframework.data.redis.core.StringRedisTemplate; // 引入 Redis 模板
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.fasterxml.jackson.databind.ObjectMapper; // 用于 JSON 序列化
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ai")
public class AiController {

    // 读取配置文件中的 Key
    @Value("${ai.dashscope.api-key}")
    private String apiKey;

    @Value("${file.upload-path}")
    private String uploadPath;
    @Autowired
    private StringRedisTemplate redisTemplate; // 注入 Redis

    @Autowired
    private ObjectMapper objectMapper; // 用于对象转 JSON 字符串
    // 线程池
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * AI 流式对话接口
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody AiRequestDto request) {
        // 设置超时时间 (3分钟)
        SseEmitter emitter = new SseEmitter(3 * 60 * 1000L);

        // 从 request 对象里取数据
        String text = request.getText();
        String type = request.getType();

        executor.execute(() -> {
            try {
                String systemPrompt = getSystemPromptByType(type);

                // 修改点 3: 既然改成了 POST，后端这里的截取限制也可以放宽到 5000 甚至更多
                // qwen-plus 很强，不用太担心 token 溢出
                String userText = text.length() > 5000 ? text.substring(0, 5000) + "..." : text;

                // ... 后面构建 Prompt 和调用 DashScope 的逻辑保持不变 ...
                // ... 注意 userContent 的构建逻辑要保留上一轮我们修改过的 "续写" 逻辑 ...

                // 1. 构建 Prompt
                String userContent;
                if ("CONTINUE".equals(type)) {
                    userContent = "这是文章的片段：\n“" + userText + "”\n\n请顺着这段话的语气和逻辑，继续往后写";
                } else {
                    userContent = "请分析以下文本：\n" + userText;
                }

                // 2. 构建 DashScope 消息
                Message systemMsg = Message.builder().role(Role.SYSTEM.getValue()).content(systemPrompt).build();
                Message userMsg = Message.builder().role(Role.USER.getValue()).content(userContent).build();

                // === 1. 动态选择模型与参数 ===
                String useModel = "qwen-plus"; // 默认用 plus (释意、提炼等)
                float repPenalty = 1.0f;       // 默认不惩罚重复

                // 如果是“续写”功能，单独“开小灶”
                if ("CONTINUE".equals(type)) {
                    useModel = "qwen-max";     // 续写用最强的 max
                    repPenalty = 1.1f;         // 续写开启重复惩罚，防止车轱辘话
                }

                // === 2. 构建 DashScope 参数 ===
                GenerationParam param = GenerationParam.builder()
                        .apiKey(apiKey)
                        .model(useModel) // <--- 修改点：这里使用变量，而不是写死
                        .messages(Arrays.asList(systemMsg, userMsg))
                        .resultFormat(GenerationParam.ResultFormat.MESSAGE)

                        // 针对续写稍微调高一点随机性，更有创意；其他模式保持稳重
                        .topP("CONTINUE".equals(type) ? 0.85 : 0.8)

                        // 传入刚才判断好的重复惩罚参数
                        .repetitionPenalty(repPenalty)

                        .maxTokens(3500)
                        .incrementalOutput(true)
                        .build();

                // 4. 发起流式调用
                Generation gen = new Generation();
                Flowable<GenerationResult> result = gen.streamCall(param);

                // 5. 监听流式返回
                result.blockingForEach(message -> {
                    // 获取当前这一小块生成的文本
                    String content = message.getOutput().getChoices().get(0).getMessage().getContent();

                    // 推送给前端
                    emitter.send(content);
                });

                // 6. 结束
                emitter.complete();

            } catch (NoApiKeyException | InputRequiredException e) {
                try {
                    emitter.send("AI 配置错误: " + e.getMessage());
                    emitter.complete();
                } catch (Exception ex) { /* ignore */ }
            } catch (Exception e) {
                try {
                    emitter.send("AI 服务繁忙，请稍后再试。");
                    emitter.complete();
                } catch (Exception ex) { /* ignore */ }
                e.printStackTrace();
            }
        });

        return emitter;
    }

    /**
     * 根据功能类型，生成不同的系统人设
     */
    private String getSystemPromptByType(String type) {
        return switch (type) {
            case "EXPLAIN" ->
                    "你是一位博学的文学教授。请对用户提供的文本进行【释意】，解释其中的生僻词、深层含义或背景典故。语言要通俗易懂。";
            case "CONTINUE" ->
                // === 修改后的续写人设 ===
                    "你是一位极具想象力的小说家。你的任务是为用户提供的文本进行【深度续写】。\n" +
                            "要求：\n" +
                            "1. 【篇幅要求】必须输出长文本，长度至少 2000 字以上，严禁草草了事。\n" +
                            "2. 【细节扩充】加入细腻的环境描写、人物心理活动刻画和生动的对话。\n" +
                            "3. 【风格一致】严格模仿原文的文笔风格（古风/科幻/悬疑等）。\n" +
                            "4. 【格式】直接输出正文，不要包含“好的”、“续写如下”等任何客套话。";
            case "SUMMARY" ->
                    "你是一位专业的编辑。请对用户提供的文本进行【提炼摘要】，用最简练的语言概括核心思想，不超过 3 点。";
            case "REWRITE" ->
                    "你是一位资深的文字润色师。请【改写】用户提供的文本，使其辞藻更优美，或者将其转换为更现代/更古风的表达（根据原文风格自动判断）。";
            case "TTS" -> "（TTS功能由单独接口处理，此处仅返回文本提示）请回复：'正在为您合成语音，请稍候...'";
            default -> "你是一个有用的AI助手。";
        };
    }
    @PostMapping("/tts")
    public Result<String> tts(@RequestBody AiRequestDto request) {
        try {
            String text = request.getText();
            if (text.length() > 300) {
                text = text.substring(0, 300);
            }

            // === 最终修正版：使用 CosyVoice 并指定音色 ===
            SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                    .apiKey(apiKey)
                    .model("cosyvoice-v1") // 指定大模型
                    // 【关键点】必须通过 parameters 指定具体音色
                    // "longxiaochun" 是阿里 CosyVoice 的标准女声音色 ID
                    .parameters(Collections.singletonMap("voice", "longxiaochun"))
                    .build();

            SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, null);
            ByteBuffer audioBuffer = synthesizer.call(text);

            if (audioBuffer == null) {
                return Result.error("500", "语音合成返回为空");
            }

            // ... 下面的保存代码完全不用变 ...
            String fileName = "tts_" + IdUtil.fastSimpleUUID() + ".mp3";
            String filePath = uploadPath + fileName;

            File file = new File(filePath);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(audioBuffer.array());
            }

            String url = "http://localhost:8090/files/" + fileName;
            return Result.success(url);

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("500", "语音合成失败: " + e.getMessage());
        }
    }
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody AiRequestDto request) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        // 必填参数校验
        Long userId = request.getUserId();
        Long bookId = request.getBookId();
        String userText = request.getText();

        // 生成 Redis Key: chat:history:用户ID:书籍ID
        // 这样每个用户在每本书里的对话都是独立的
        String redisKey = "chat:history:" + userId + ":" + bookId;

        executor.execute(() -> {
            try {
                // 1. 从 Redis 获取历史消息 (取最近 10 条，防止 Token 溢出)
                // List 结构: [JSON(Message), JSON(Message), ...]
                List<String> historyJson = redisTemplate.opsForList().range(redisKey, -10, -1);

                List<Message> messages = new ArrayList<>();

                // 添加系统人设 (System Prompt)
                messages.add(Message.builder().role(Role.SYSTEM.getValue())
                        .content("你是一个读书助手。请基于用户的提问和上下文进行回答。").build());

                // 解析历史消息并加入列表
                if (historyJson != null) {
                    for (String json : historyJson) {
                        try {
                            // 这里假设存的是 DashScope 的 Message 对象结构
                            // 实际存储建议只存 role 和 content 的简单对象，这里为了演示简化直接反序列化
                            // 注意：实际项目中建议建一个简单的 POJO (role, content) 来存，不要直接存 SDK 的 Message
                            // 这里为了演示流程，我们手动构建一下
                            ChatMsgPojo pojo = objectMapper.readValue(json, ChatMsgPojo.class);
                            messages.add(Message.builder().role(pojo.getRole()).content(pojo.getContent()).build());
                        } catch (Exception e) { /* 忽略解析错误 */ }
                    }
                }

                // 添加当前用户的提问
                Message currentUserMsg = Message.builder().role(Role.USER.getValue()).content(userText).build();
                messages.add(currentUserMsg);

                // 2. 调用大模型
                GenerationParam param = GenerationParam.builder()
                        .apiKey(apiKey)
                        .model("qwen-plus") // 对话建议用 Plus 或 Max
                        .messages(messages) // 传入包含历史的完整消息列表
                        .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                        .incrementalOutput(true)
                        .build();

                Generation gen = new Generation();
                Flowable<GenerationResult> result = gen.streamCall(param);

                // 用于收集 AI 的完整回复，以便存入 Redis
                StringBuilder aiFullResponse = new StringBuilder();

                result.blockingForEach(message -> {
                    String content = message.getOutput().getChoices().get(0).getMessage().getContent();
                    aiFullResponse.append(content);
                    emitter.send(content);
                });

                // 3. 对话结束后，将新交互存入 Redis (异步)
                // 存用户的话
                redisTemplate.opsForList().rightPush(redisKey, objectMapper.writeValueAsString(new ChatMsgPojo("user", userText)));
                // 存 AI 的话
                redisTemplate.opsForList().rightPush(redisKey, objectMapper.writeValueAsString(new ChatMsgPojo("assistant", aiFullResponse.toString())));

                // 设置过期时间 (比如 24 小时后对话失效)
                redisTemplate.expire(redisKey, 24, TimeUnit.HOURS);

                emitter.complete();

            } catch (Exception e) {
                e.printStackTrace();
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // 简单的内部类，用于 Redis 序列化
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChatMsgPojo {
        private String role;
        private String content;
    }
}