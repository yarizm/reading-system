package com.example.reading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "tts")
public class TtsProperties {

    private String provider = "lightweight";
    private final Lightweight lightweight = new Lightweight();
    private final DashScope dashscope = new DashScope();

    @Data
    public static class Lightweight {
        private String baseUrl = "http://localhost:8091";
        private long timeoutMs = 60000;
    }

    @Data
    public static class DashScope {
        private boolean enabled = true;
    }
}
