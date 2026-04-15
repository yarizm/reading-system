package com.example.reading.service.tts;

import com.example.reading.config.TtsProperties;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class LightweightTtsProvider implements TtsProvider {

    private final TtsProperties ttsProperties;
    private final WebClient webClient;

    public LightweightTtsProvider(TtsProperties ttsProperties) {
        this.ttsProperties = ttsProperties;
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(ttsProperties.getLightweight().getTimeoutMs()));
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(ttsProperties.getLightweight().getBaseUrl())
                .build();
    }

    @Override
    public String getProviderName() {
        return "lightweight";
    }

    @Override
    public String getFileExtension() {
        return "mp3";
    }

    @Override
    public void synthesizeToFile(String text, String voiceKey, File outputFile) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        body.put("voice", mapVoice(voiceKey));

        if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }

        Boolean wrote = webClient.post()
                .uri("/synthesize")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.valueOf("audio/mpeg"), MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class)
                .as(dataBuffers -> DataBufferUtils.write(dataBuffers, outputFile.toPath()))
                .thenReturn(Boolean.TRUE)
                .block(Duration.ofMillis(ttsProperties.getLightweight().getTimeoutMs()));

        if (!Boolean.TRUE.equals(wrote) || !outputFile.exists() || outputFile.length() == 0) {
            throw new IllegalStateException("Lightweight TTS service returned empty audio");
        }
    }

    private String mapVoice(String voiceKey) {
        if (voiceKey == null) {
            return "zh-CN-XiaoxiaoNeural";
        }
        return switch (voiceKey.toLowerCase()) {
            case "zhiqi" -> "zh-CN-XiaoyiNeural";
            case "zhiying" -> "zh-CN-XiaoxiaoNeural";
            case "zhiyuan" -> "zh-CN-YunxiNeural";
            default -> "zh-CN-XiaoxiaoNeural";
        };
    }
}
