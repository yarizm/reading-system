package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.AiGeneratedContent;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.IAiGeneratedContentService;
import com.example.reading.service.InsightService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/insight")
@CrossOrigin
public class InsightController {

    @Autowired
    private AuthContextService authContextService;

    @Autowired
    private InsightService insightService;

    @Autowired
    private IAiGeneratedContentService aiGeneratedContentService;

    @Value("${dify.insight.api-url}")
    private String difyWorkflowUrl;

    @Value("${dify.insight.api-key}")
    private String difyApiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InsightController(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    @PostMapping("/generate")
    public Mono<Map<String, Object>> generateInsight(HttpServletRequest httpRequest) {
        Long currentUserId = authContextService.currentUserId(httpRequest);
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        Map<String, Object> stats = insightService.collectUserReadingStats(currentUserId);
        
        String statsJson;
        try {
            statsJson = objectMapper.writeValueAsString(stats);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "无法处理统计数据");
        }

        Map<String, Object> payload = new HashMap<>();
        Map<String, String> inputs = new HashMap<>();
        inputs.put("reading_stats", statsJson);
        
        payload.put("inputs", inputs);
        payload.put("response_mode", "blocking");
        payload.put("user", "user-" + currentUserId);

        return webClient.post()
                .uri(difyWorkflowUrl + "/workflows/run")
                .header("Authorization", "Bearer " + difyApiKey)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .publishOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .map(response -> {
                    Map<String, Object> data = (Map<String, Object>) response.get("data");
                    if (data != null) {
                        Map<String, Object> outputs = (Map<String, Object>) data.get("outputs");
                        if (outputs != null && outputs.containsKey("report")) {
                            String reportText = (String) outputs.get("report");

                            AiGeneratedContent content = new AiGeneratedContent();
                            content.setUserId(currentUserId);
                            content.setContentType("insight_report");
                            content.setReferenceType("user");
                            content.setReferenceId(currentUserId);
                            content.setTitle("阅读洞察报告");
                            content.setContent(reportText);
                            content.setCreateTime(java.time.LocalDateTime.now());
                            aiGeneratedContentService.save(content);
                            
                            Map<String, Object> finalRes = new HashMap<>();
                            finalRes.put("id", content.getId());
                            finalRes.put("report", reportText);
                            finalRes.put("createTime", content.getCreateTime());
                            return finalRes;
                        }
                    }
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Dify 返回格式不正确");
                });
    }

    @GetMapping("/latest")
    public Map<String, Object> getLatestInsight(HttpServletRequest httpRequest) {
        Long currentUserId = authContextService.currentUserId(httpRequest);
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        
        QueryWrapper<AiGeneratedContent> query = new QueryWrapper<>();
        query.eq("user_id", currentUserId);
        query.eq("content_type", "insight_report");
        query.orderByDesc("create_time");
        query.last("LIMIT 1");
        
        AiGeneratedContent content = aiGeneratedContentService.getOne(query);
        if (content == null) {
            Map<String, Object> res = new HashMap<>();
            res.put("hasReport", false);
            return res;
        }
        
        Map<String, Object> res = new HashMap<>();
        res.put("hasReport", true);
        res.put("id", content.getId());
        res.put("report", content.getContent());
        res.put("createTime", content.getCreateTime());
        return res;
    }
    
    @GetMapping("/history")
    public List<AiGeneratedContent> getInsightHistory(HttpServletRequest httpRequest) {
        Long currentUserId = authContextService.currentUserId(httpRequest);
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        
        QueryWrapper<AiGeneratedContent> query = new QueryWrapper<>();
        query.eq("user_id", currentUserId);
        query.eq("content_type", "insight_report");
        query.orderByDesc("create_time");
        
        return aiGeneratedContentService.list(query);
    }
}
