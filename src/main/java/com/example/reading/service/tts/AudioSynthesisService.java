package com.example.reading.service.tts;

import cn.hutool.crypto.digest.DigestUtil;
import com.example.reading.common.Result;
import com.example.reading.config.TtsProperties;
import com.example.reading.dto.AiRequestDto;
import com.example.reading.dto.AudioGenerateRequest;
import com.example.reading.dto.AudioGenerateResponse;
import com.example.reading.entity.SysChapter;
import com.example.reading.mapper.SysChapterMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
public class AudioSynthesisService {

    private final List<TtsProvider> providers;
    private final TtsProperties ttsProperties;
    private final SysChapterMapper chapterMapper;

    @Value("${file.upload-path}")
    private String uploadPath;

    @Value("${server.port:8090}")
    private String serverPort;

    public AudioSynthesisService(List<TtsProvider> providers,
                                 TtsProperties ttsProperties,
                                 SysChapterMapper chapterMapper) {
        this.providers = providers;
        this.ttsProperties = ttsProperties;
        this.chapterMapper = chapterMapper;
    }

    public Result<String> tts(AiRequestDto request) {
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

    public Result<AudioGenerateResponse> generateAudio(AudioGenerateRequest request) {
        try {
            String text = normalizeText(request.getText());
            if (text.isEmpty()) {
                return Result.error("500", "朗读内容不能为空");
            }

            ensureUploadDir();
            String voiceKey = normalizeVoiceKey(request.getVoice());
            TtsProvider provider = resolveProvider();

            File outputFile = isChapterRequest(request)
                    ? buildChapterCacheFile(request.getChapterId(), voiceKey, provider)
                    : buildSnippetFile(voiceKey, text, provider);

            boolean cached = outputFile.exists();
            if (!cached) {
                provider.synthesizeToFile(text, voiceKey, outputFile);
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
            return Result.error("500", "当前听书服务不可用，请稍后再试");
        }
    }

    private TtsProvider resolveProvider() {
        String providerName = ttsProperties.getProvider() == null ? "lightweight" : ttsProperties.getProvider().trim().toLowerCase();
        return providers.stream()
                .filter(provider -> provider.getProviderName().equalsIgnoreCase(providerName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unsupported TTS provider: " + providerName));
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

    private File buildChapterCacheFile(Long chapterId, String voiceKey, TtsProvider provider) {
        return new File(uploadPath,
                "chapter_tts_" + provider.getProviderName() + "_" + chapterId + "_" + voiceKey + "." + provider.getFileExtension());
    }

    private File buildSnippetFile(String voiceKey, String text, TtsProvider provider) {
        String hash = DigestUtil.md5Hex(text + "|" + voiceKey + "|" + provider.getProviderName());
        return new File(uploadPath, "snippet_tts_" + provider.getProviderName() + "_" + hash + "." + provider.getFileExtension());
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
            return "第 " + (request.getChapterIndex() + 1) + " 章听书";
        }
        if ("paragraph".equalsIgnoreCase(request.getSourceType()) && request.getParagraphIndex() != null) {
            return "第 " + (request.getParagraphIndex() + 1) + " 段朗读";
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

    private String buildFileUrl(String fileName) {
        return "http://localhost:" + serverPort + "/files/" + fileName;
    }
}
