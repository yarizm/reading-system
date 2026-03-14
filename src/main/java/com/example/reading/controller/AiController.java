package com.example.reading.controller;
import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
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
import com.example.reading.common.Result;
import com.example.reading.entity.SysChapter;
import com.example.reading.mapper.SysChapterMapper;
import cn.hutool.core.util.IdUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import org.springframework.data.redis.core.StringRedisTemplate; // 引入 Redis 模板
import com.fasterxml.jackson.databind.ObjectMapper; // 用于 JSON 序列化
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    private SysChapterMapper chapterMapper; // 注入章节Mapper

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

    private AudioParameters.Voice getVoiceConstant(String voiceStr) {
        if (voiceStr == null) return AudioParameters.Voice.CHERRY;
        return switch (voiceStr.toLowerCase()) {
            case "zhiqi" -> AudioParameters.Voice.ETHAN;
            case "zhiying" -> AudioParameters.Voice.DYLAN;
            case "zhiyuan" -> AudioParameters.Voice.KIKI;
            default -> AudioParameters.Voice.CHERRY;
        };
    }

    @PostMapping("/tts")
    public Result<String> tts(@RequestBody AiRequestDto request) {
        try {
            String text = request.getText();
            AudioParameters.Voice voice = getVoiceConstant(request.getVoice());
            if (text.length() > 300) {
                text = text.substring(0, 300);
            }

            MultiModalConversation conv = new MultiModalConversation();
            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(apiKey)
                    .model("qwen-tts")
                    .text(text)
                    .voice(voice)
                    .build();

            MultiModalConversationResult result = conv.call(param);
            String audioUrl = result.getOutput().getAudio().getUrl();

            if (audioUrl == null) {
                return Result.error("500", "语音合成返回为空");
            }

            // ... 下面的保存代码完全不用变 ...
            String fileName = "tts_" + IdUtil.fastSimpleUUID() + ".wav";
            String filePath = uploadPath + fileName;

            File file = new File(filePath);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            try (InputStream in = new URL(audioUrl).openStream();
                 FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            String url = "http://localhost:8090/files/" + fileName;
            return Result.success(url);

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("500", "语音合成失败: " + e.getMessage());
        }
    }

    /**
     * 获取整章 TTS 语音（异步处理 + Redis 状态跟踪）
     */
    @GetMapping("/chapter_tts/{chapterId}")
    public Result<String> chapterTts(@PathVariable Long chapterId, @RequestParam(required = false, defaultValue = "cherry") String voiceParam) {
        try {
            // 1. 检查 Redis 中是否已经有正在生成或已完成的状态
            String statusKey = "tts:status:" + chapterId + ":" + voiceParam;
            String status = redisTemplate.opsForValue().get(statusKey);

            if ("processing".equals(status)) {
                return Result.success("processing");
            }

            SysChapter chapter = chapterMapper.selectById(chapterId);
            if (chapter == null) {
                return Result.error("404", "章节不存在");
            }

            // 2. 如果已有缓存，进行文件存在性校验
            if (chapter.getAudioUrl() != null && !chapter.getAudioUrl().trim().isEmpty() && chapter.getAudioUrl().contains(voiceParam)) {
                String existingUrl = chapter.getAudioUrl();
                String fileName = existingUrl.substring(existingUrl.lastIndexOf("/") + 1);
                File file = new File(uploadPath + fileName);
                
                if (file.exists() && file.length() > 0) {
                    redisTemplate.opsForValue().set(statusKey, existingUrl, 24, TimeUnit.HOURS);
                    return Result.success(existingUrl);
                } else {
                    System.out.println("检测到数据库记录存在但物理文件丢失或损坏，准备重新生成: " + file.getAbsolutePath());
                    // 文件不存在，清空数据库记录以便重新生成（可选，这里直接往下走重新生成逻辑即可）
                }
            }

            // 3. 开启异步任务进行合成
            redisTemplate.opsForValue().set(statusKey, "processing", 10, TimeUnit.MINUTES);
            
            executor.execute(() -> {
                System.out.println("====== 开始异步生成整章语音: 章节ID " + chapterId + " =====");
                long startTime = System.currentTimeMillis();
                try {
                    String text = chapter.getContent();
                    if (text == null || text.trim().isEmpty()) {
                        System.err.println("章节内容为空");
                        redisTemplate.opsForValue().set(statusKey, "error:内容为空", 5, TimeUnit.MINUTES);
                        return;
                    }

                    List<String> textChunks = splitText(text, 300);
                    System.out.println("文章已切分为 " + textChunks.size() + " 个片段，开始并行合成...");

                    // 恢复为顺序执行，并加上延时以防止触发 Qwen API 并发/频率限制
                    List<String> audioUrls = new ArrayList<>();
                    for (int i = 0; i < textChunks.size(); i++) {
                        String chunk = textChunks.get(i);
                        if (chunk == null || chunk.trim().isEmpty()) continue;

                        System.out.println("  -> 开始合成片段 " + (i + 1) + "/" + textChunks.size());
                        try {
                            MultiModalConversation conv = new MultiModalConversation();
                            MultiModalConversationParam param = MultiModalConversationParam.builder()
                                    .apiKey(apiKey)
                                    .model("qwen-tts")
                                    .text(chunk)
                                    .voice(getVoiceConstant(voiceParam))
                                    .build();

                            MultiModalConversationResult result = conv.call(param);
                            String audioUrl = result.getOutput().getAudio().getUrl();
                            if (audioUrl != null) {
                                audioUrls.add(audioUrl);
                                System.out.println("  <- 片段 " + (i + 1) + " 合成成功: " + audioUrl);
                            } else {
                                System.err.println("  <- 片段 " + (i + 1) + " 返回的 URL 为空");
                            }
                            
                            // 加上一个短延时，稍微缓解 API 压力
                            if (i < textChunks.size() - 1) {
                                Thread.sleep(500); 
                            }
                        } catch (Exception e) {
                            System.err.println("  <- 片段 " + (i + 1) + " 合成异常: " + e.getMessage());
                        }
                    }

                    System.out.println("所有片段合成完成，成功 " + audioUrls.size() + "/" + textChunks.size() + "。开始合并...");

                    if (!audioUrls.isEmpty()) {
                        String fileName = "chapter_tts_" + voiceParam + "_" + chapterId + "_" + IdUtil.fastSimpleUUID() + ".wav";
                        String filePath = uploadPath + fileName;

                        File file = new File(filePath);
                        if (!file.getParentFile().exists()) {
                            file.getParentFile().mkdirs();
                        }

                        mergeWavAudioStreams(audioUrls, filePath);
                        System.out.println("合并完成! 耗时: " + (System.currentTimeMillis() - startTime) + "ms, 文件: " + filePath);
                        
                        String url = "http://localhost:8090/files/" + fileName;
                        
                        // 写入数据库
                        chapter.setAudioUrl(url);
                        chapterMapper.updateById(chapter);
                        
                        // 更新 Redis 状态为完成后的 URL
                        redisTemplate.opsForValue().set(statusKey, url, 24, TimeUnit.HOURS);
                        System.out.println("====== 章节 " + chapterId + " 语音任务结束 =====");
                    } else {
                        System.err.println("合成失败: 没有任何成功的音频片段");
                        redisTemplate.opsForValue().set(statusKey, "error:所有片段合成失败", 5, TimeUnit.MINUTES);
                    }

                } catch (Exception e) {
                    System.err.println("异步生成任务异常: " + e.getMessage());
                    e.printStackTrace();
                    redisTemplate.opsForValue().set(statusKey, "error:" + e.getMessage(), 5, TimeUnit.MINUTES);
                }
            });

            return Result.success("processing");

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("500", "章节语音合成初始化失败: " + e.getMessage());
        }
    }

    /**
     * 查询 TTS 合成进度状态
     */
    @GetMapping("/chapter_tts/status/{chapterId}")
    public Result<String> getTtsStatus(@PathVariable Long chapterId, @RequestParam(required = false, defaultValue = "cherry") String voiceParam) {
        String statusKey = "tts:status:" + chapterId + ":" + voiceParam;
        String status = redisTemplate.opsForValue().get(statusKey);
        
        if (status == null) {
            // 如果缓存没了，去库里查一眼，也要校验物理文件
            SysChapter chapter = chapterMapper.selectById(chapterId);
            if (chapter != null && chapter.getAudioUrl() != null && chapter.getAudioUrl().contains(voiceParam)) {
                String existingUrl = chapter.getAudioUrl();
                String fileName = existingUrl.substring(existingUrl.lastIndexOf("/") + 1);
                if (new File(uploadPath + fileName).exists()) {
                    return Result.success(existingUrl);
                }
            }
            return Result.success("not_found");
        }
        
        return Result.success(status);
    }

    /**
     * 合并多个 WAV 流：
     * 1. 使用第一个分片的头部作为基准。
     * 2. 拼接所有分片的数据部分。
     * 3. 修正头部中的文件长度和数据长度。
     */
    private void mergeWavAudioStreams(List<String> audioUrls, String filePath) throws Exception {
        long totalDataSize = 0;
        byte[] firstHeader = null;

        File tempFile = new File(filePath + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            for (int i = 0; i < audioUrls.size(); i++) {
                try (InputStream in = new URL(audioUrls.get(i)).openStream()) {
                    byte[] data = in.readAllBytes();
                    if (data.length < 44) continue;

                    if (i == 0) {
                        firstHeader = new byte[44];
                        System.arraycopy(data, 0, firstHeader, 0, 44);
                    }

                    // WAV 文件的 PCM 数据通常从 44 字节开始 (标准)
                    // 但稳妥起见，寻找 "data" 标志
                    int dataOffset = findDataOffset(data);
                    if (dataOffset != -1) {
                        int chunkSize = data.length - dataOffset - 4;
                        byte[] pcmData = new byte[chunkSize];
                        System.arraycopy(data, dataOffset + 4, pcmData, 0, chunkSize);
                        fos.write(pcmData);
                        totalDataSize += chunkSize;
                    }
                }
            }
        }

        if (firstHeader != null) {
            // 修改头部长度信息
            // RIFF chunk size = 36 + totalDataSize
            long riffSize = 36 + totalDataSize;
            firstHeader[4] = (byte) (riffSize & 0xff);
            firstHeader[5] = (byte) ((riffSize >> 8) & 0xff);
            firstHeader[6] = (byte) ((riffSize >> 16) & 0xff);
            firstHeader[7] = (byte) ((riffSize >> 24) & 0xff);

            // data subchunk size = totalDataSize
            firstHeader[40] = (byte) (totalDataSize & 0xff);
            firstHeader[41] = (byte) ((totalDataSize >> 8) & 0xff);
            firstHeader[42] = (byte) ((totalDataSize >> 16) & 0xff);
            firstHeader[43] = (byte) ((totalDataSize >> 24) & 0xff);

            try (FileOutputStream finalFos = new FileOutputStream(filePath)) {
                finalFos.write(firstHeader);
                try (FileInputStream fis = new FileInputStream(tempFile)) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        finalFos.write(buffer, 0, len);
                    }
                }
            }
        }
        tempFile.delete();
    }

    private int findDataOffset(byte[] data) {
        for (int i = 0; i < data.length - 4; i++) {
            if (data[i] == 'd' && data[i+1] == 'a' && data[i+2] == 't' && data[i+3] == 'a') {
                return i;
            }
        }
        return -1;
    }


    /**
     * 将长文本按照标点符号切分成小段，防止超过大模型单次合成字数限制
     */
    private List<String> splitText(String text, int maxLength) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        // 以句号、感叹号、问号、换行符等作为切分符
        String[] sentences = text.split("(?<=[。！？\\n])");
        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : sentences) {
            // 清理无意义的空白
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;

            // 检查加上这一句后是否会超过 maxLength
            if (currentChunk.length() + sentence.length() > maxLength) {
                // 如果当下的 chunk 不空，保存当前的，并开启一个新的
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString());
                    currentChunk.setLength(0); // 清空
                }

                // 如果单条句子本身就超过了 maxLength, 强行按长度截断
                while (sentence.length() > maxLength) {
                    chunks.add(sentence.substring(0, maxLength));
                    sentence = sentence.substring(maxLength);
                }
            }

            currentChunk.append(sentence);
        }

        // 把最后剩下的内容加进去
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }

        return chunks;
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