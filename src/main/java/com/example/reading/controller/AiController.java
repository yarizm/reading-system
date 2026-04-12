package com.example.reading.controller;

import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import cn.hutool.core.util.IdUtil;
import com.example.reading.common.Result;
import com.example.reading.dto.AiRequestDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;

/**
 * AI 能力控制器
 * 目前仅提供选中文本的 TTS 语音合成功能（调用通义千问 qwen-tts 模型）。
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    @Value("${ai.dashscope.api-key}")
    private String apiKey;

    @Value("${file.upload-path}")
    private String uploadPath;

    /** 将音色字符串映射为 SDK 枚举值 */
    private AudioParameters.Voice getVoiceConstant(String voiceStr) {
        if (voiceStr == null) return AudioParameters.Voice.CHERRY;
        return switch (voiceStr.toLowerCase()) {
            case "zhiqi" -> AudioParameters.Voice.ETHAN;
            case "zhiying" -> AudioParameters.Voice.DYLAN;
            case "zhiyuan" -> AudioParameters.Voice.KIKI;
            default -> AudioParameters.Voice.CHERRY;
        };
    }

    /**
     * 短文本语音合成
     * 将选中文本（最多 300 字）合成为 WAV 音频文件并返回可访问 URL。
     */
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
}