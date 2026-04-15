package com.example.reading.controller;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.example.reading.common.Result;
import com.example.reading.dto.AiRequestDto;
import com.example.reading.dto.AudioGenerateRequest;
import com.example.reading.dto.AudioGenerateResponse;
import com.example.reading.entity.SysChapter;
import com.example.reading.mapper.SysChapterMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/ai")
public class AiController {

    private static final int MAX_SEGMENT_CHARS = 220;

    @Value("${ai.dashscope.api-key}")
    private String apiKey;

    @Value("${file.upload-path}")
    private String uploadPath;

    @Autowired
    private SysChapterMapper chapterMapper;

    private AudioParameters.Voice getVoiceConstant(String voiceStr) {
        if (voiceStr == null) {
            return AudioParameters.Voice.CHERRY;
        }
        return switch (voiceStr.toLowerCase()) {
            case "zhiqi" -> AudioParameters.Voice.ETHAN;
            case "zhiying" -> AudioParameters.Voice.DYLAN;
            case "zhiyuan" -> AudioParameters.Voice.KIKI;
            default -> AudioParameters.Voice.CHERRY;
        };
    }

    @PostMapping("/tts")
    public Result<String> tts(@RequestBody AiRequestDto request) {
        AudioGenerateRequest audioRequest = new AudioGenerateRequest();
        audioRequest.setText(request.getText());
        audioRequest.setVoice(request.getVoice());
        audioRequest.setBookId(request.getBookId());
        audioRequest.setSourceType("paragraph");

        Result<AudioGenerateResponse> result = generateAudio(audioRequest);
        if (!"200".equals(result.getCode()) || result.getData() == null) {
            return Result.error(result.getCode(), result.getMsg());
        }
        return Result.success(result.getData().getAudioUrl());
    }

    @PostMapping("/audio/generate")
    public Result<AudioGenerateResponse> generateAudio(@RequestBody AudioGenerateRequest request) {
        try {
            String text = normalizeText(request.getText());
            if (text.isEmpty()) {
                return Result.error("500", "朗读内容不能为空");
            }

            String voiceKey = normalizeVoiceKey(request.getVoice());
            File outputFile;
            boolean cached = false;

            if (isChapterRequest(request)) {
                outputFile = buildChapterCacheFile(request.getChapterId(), voiceKey);
                if (outputFile.exists()) {
                    cached = true;
                } else {
                    synthesizeTextToFile(text, getVoiceConstant(voiceKey), outputFile);
                }
            } else {
                outputFile = buildSnippetFile(voiceKey, text);
                if (outputFile.exists()) {
                    cached = true;
                } else {
                    synthesizeTextToFile(text, getVoiceConstant(voiceKey), outputFile);
                }
            }

            AudioGenerateResponse response = new AudioGenerateResponse();
            response.setAudioUrl(buildFileUrl(outputFile.getName()));
            response.setTitle(resolveAudioTitle(request));
            response.setSourceType(request.getSourceType());
            response.setBookId(request.getBookId());
            response.setChapterId(request.getChapterId());
            response.setChapterIndex(request.getChapterIndex());
            response.setParagraphIndex(request.getParagraphIndex());
            response.setCached(cached);

            updateChapterAudioCache(request, response.getAudioUrl());
            return Result.success(response);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("500", "语音合成失败: " + e.getMessage());
        }
    }

    private boolean isChapterRequest(AudioGenerateRequest request) {
        return "chapter".equalsIgnoreCase(request.getSourceType()) && request.getChapterId() != null;
    }

    private void updateChapterAudioCache(AudioGenerateRequest request, String audioUrl) {
        if (!isChapterRequest(request)) {
            return;
        }
        if (request.getVoice() != null && !"cherry".equalsIgnoreCase(request.getVoice())) {
            return;
        }

        SysChapter chapter = chapterMapper.selectById(request.getChapterId());
        if (chapter == null || audioUrl.equals(chapter.getAudioUrl())) {
            return;
        }

        chapter.setAudioUrl(audioUrl);
        chapterMapper.updateById(chapter);
    }

    private File buildChapterCacheFile(Long chapterId, String voiceKey) {
        ensureUploadDir();
        return new File(uploadPath, "chapter_tts_" + chapterId + "_" + voiceKey + ".wav");
    }

    private File buildSnippetFile(String voiceKey, String text) {
        ensureUploadDir();
        String hash = DigestUtil.md5Hex(text + "|" + voiceKey);
        return new File(uploadPath, "snippet_tts_" + hash + ".wav");
    }

    private void ensureUploadDir() {
        File dir = new File(uploadPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private String resolveAudioTitle(AudioGenerateRequest request) {
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            return request.getTitle().trim();
        }
        if ("chapter".equalsIgnoreCase(request.getSourceType()) && request.getChapterIndex() != null) {
            return "第" + (request.getChapterIndex() + 1) + "章听书";
        }
        if ("paragraph".equalsIgnoreCase(request.getSourceType()) && request.getParagraphIndex() != null) {
            return "第" + (request.getParagraphIndex() + 1) + "段朗读";
        }
        return "朗读音频";
    }

    private String normalizeVoiceKey(String voice) {
        return voice == null || voice.isBlank() ? "cherry" : voice.toLowerCase();
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r", "")
                .replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void synthesizeTextToFile(String text, AudioParameters.Voice voice, File outputFile) throws Exception {
        List<String> segments = splitText(text);
        List<File> tempFiles = new ArrayList<>();

        try {
            for (String segment : segments) {
                File tempFile = new File(uploadPath, "tts_tmp_" + IdUtil.fastSimpleUUID() + ".wav");
                synthesizeSegmentToFile(segment, voice, tempFile);
                tempFiles.add(tempFile);
            }

            if (tempFiles.isEmpty()) {
                throw new IllegalStateException("未生成任何音频片段");
            }

            if (tempFiles.size() == 1) {
                Files.copy(tempFiles.get(0).toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                mergeWavFiles(tempFiles, outputFile);
            }
        } finally {
            for (File tempFile : tempFiles) {
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }
        }
    }

    private void synthesizeSegmentToFile(String text, AudioParameters.Voice voice, File outputFile) throws Exception {
        MultiModalConversation conversation = new MultiModalConversation();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(apiKey)
                .model("qwen-tts")
                .text(text)
                .voice(voice)
                .build();

        MultiModalConversationResult result = conversation.call(param);
        String audioUrl = result.getOutput().getAudio().getUrl();
        if (audioUrl == null) {
            throw new IllegalStateException("语音合成返回为空");
        }

        try (InputStream in = new URL(audioUrl).openStream();
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
    }

    private List<String> splitText(String text) {
        if (text.length() <= MAX_SEGMENT_CHARS) {
            return Collections.singletonList(text);
        }

        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            current.append(ch);

            boolean punctuationBreak = "。！？；;,.，、!?".indexOf(ch) >= 0
                    && current.length() >= MAX_SEGMENT_CHARS / 2;
            boolean hardBreak = current.length() >= MAX_SEGMENT_CHARS;

            if (punctuationBreak || hardBreak) {
                segments.add(current.toString().trim());
                current.setLength(0);
            }
        }

        if (!current.isEmpty()) {
            segments.add(current.toString().trim());
        }
        return segments;
    }

    private void mergeWavFiles(List<File> wavFiles, File targetFile) throws Exception {
        List<AudioInputStream> streams = new ArrayList<>();
        try {
            for (File wavFile : wavFiles) {
                streams.add(AudioSystem.getAudioInputStream(wavFile));
            }

            AudioInputStream firstStream = streams.get(0);
            long totalFrameLength = 0L;
            for (AudioInputStream stream : streams) {
                totalFrameLength += stream.getFrameLength();
            }

            SequenceInputStream sequenceInputStream = new SequenceInputStream(Collections.enumeration(streams));
            try (AudioInputStream appendedStream = new AudioInputStream(
                    sequenceInputStream,
                    firstStream.getFormat(),
                    totalFrameLength
            )) {
                AudioSystem.write(appendedStream, AudioFileFormat.Type.WAVE, targetFile);
            }
        } finally {
            for (AudioInputStream stream : streams) {
                try {
                    stream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String buildFileUrl(String fileName) {
        return "http://localhost:8090/files/" + fileName;
    }
}
