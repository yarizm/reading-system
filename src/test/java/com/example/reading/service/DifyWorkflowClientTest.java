package com.example.reading.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DifyWorkflowClientTest {

    private final DifyWorkflowClient client = new DifyWorkflowClient(WebClient.builder());

    @Test
    void extractOutputsReturnsWorkflowOutputs() {
        @SuppressWarnings("unchecked")
        Map<String, Object> outputs = ReflectionTestUtils.invokeMethod(
                client,
                "extractOutputs",
                Map.of("data", Map.of("outputs", Map.of("result", "ok", "score", 3)))
        );

        assertThat(outputs).containsEntry("result", "ok").containsEntry("score", 3);
    }

    @Test
    void extractOutputsReportsDifyWorkflowAppTypeError() {
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                client,
                "extractOutputs",
                Map.of("code", "not_workflow_app", "message", "bad app")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException ex = (ResponseStatusException) error;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(ex.getReason()).contains("不是 Workflow 应用");
                });
    }

    @Test
    void runBlockingFailsFastWhenWorkflowConfigIsMissing() {
        assertThatThrownBy(() -> client.runBlocking("", "key", Map.of(), "user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException ex = (ResponseStatusException) error;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(ex.getReason()).contains("未配置");
                });
    }
}
