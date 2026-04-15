package com.example.reading.service.tts;

import cn.hutool.core.util.IdUtil;
import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.example.reading.config.TtsProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

@Component
public class DashScopeTtsProvider implements TtsProvider {

    private static final int MAX_SEGMENT_CHARS = 220;

    @Value("${ai.dashscope.api-key}")
    private String apiKey;

    @Value("${file.upload-path}")
    private String uploadPath;

    private final TtsProperties ttsProperties;

    public DashScopeTtsProvider(TtsProperties ttsProperties) {
        this.ttsProperties = ttsProperties;
    }

    @Override
    public String getProviderName() {
        return "dashscope";
    }

    @Override
    public String getFileExtension() {
        return "wav";
    }

    @Override
    public void synthesizeToFile(String text, String voiceKey, File outputFile) throws Exception {
        if (!ttsProperties.getDashscope().isEnabled()) {
            throw new IllegalStateException("DashScope TTS is disabled");
        }

        List<String> segments = splitText(text);
        List<File> tempFiles = new ArrayList<>();

        try {
            for (String segment : segments) {
                File tempFile = new File(uploadPath, "tts_tmp_" + IdUtil.fastSimpleUUID() + ".wav");
                synthesizeSegmentToFile(segment, getVoiceConstant(voiceKey), tempFile);
                tempFiles.add(tempFile);
            }

            if (tempFiles.isEmpty()) {
                throw new IllegalStateException("No audio segment generated");
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
            throw new IllegalStateException("Empty audio url from DashScope");
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

            boolean punctuationBreak = "。！；;,.，、？?".indexOf(ch) >= 0
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
}
