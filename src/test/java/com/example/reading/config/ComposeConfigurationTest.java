package com.example.reading.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ComposeConfigurationTest {

    @Test
    void defaultComposeDoesNotRequireExternalDifyNetwork() throws Exception {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose).doesNotContain("docker_default");
        assertThat(compose).doesNotContain("external: true");
        assertThat(compose).contains("DIFY_BASE_URL: ${DIFY_BASE_URL:-http://localhost/v1}");
    }

    @Test
    void difyOverrideKeepsExternalNetworkConfiguration() throws Exception {
        String compose = Files.readString(Path.of("docker-compose.dify.yml"));

        assertThat(compose).contains("external: true");
        assertThat(compose).contains("DIFY_DOCKER_NETWORK:-docker_default");
        assertThat(compose).contains("DIFY_BASE_URL: ${DIFY_DOCKER_BASE_URL:-http://docker-api-1:5001/v1}");
    }
}
