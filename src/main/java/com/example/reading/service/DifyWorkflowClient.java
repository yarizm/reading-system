package com.example.reading.service;

import com.example.reading.utils.DifyUrlUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service("appDifyWorkflowClient")
public class DifyWorkflowClient {

    private final WebClient webClient;

    public DifyWorkflowClient(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    public Mono<Map<String, Object>> runBlocking(String baseUrl,
                                                 String apiKey,
                                                 Map<String, ?> inputs,
                                                 String user) {
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(apiKey)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Dify Workflow 未配置");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("inputs", inputs != null ? inputs : Map.of());
        payload.put("response_mode", "blocking");
        payload.put("user", user);

        return webClient.post()
                .uri(DifyUrlUtils.trimTrailingSlash(baseUrl) + "/workflows/run")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .publishOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .map(this::extractOutputs);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractOutputs(Map<?, ?> response) {
        if (response.containsKey("code") && !response.containsKey("data")) {
            String difyCode = String.valueOf(response.get("code"));
            Object message = response.get("message");
            String difyMsg = message != null ? String.valueOf(message) : "未知错误";
            if ("not_workflow_app".equals(difyCode)) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Dify 应用类型错误：配置的 KEY 对应的不是 Workflow 应用，请在 Dify 后台确认");
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Dify 错误: [" + difyCode + "] " + difyMsg);
        }

        Object dataObject = response.get("data");
        if (dataObject instanceof Map<?, ?> data) {
            Object outputsObject = data.get("outputs");
            if (outputsObject instanceof Map<?, ?> outputs) {
                Map<String, Object> result = new HashMap<>();
                outputs.forEach((key, value) -> result.put(String.valueOf(key), value));
                return result;
            }
        }

        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Dify 返回格式不正确");
    }
}
