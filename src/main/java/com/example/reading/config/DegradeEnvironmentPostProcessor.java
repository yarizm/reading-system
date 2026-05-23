package com.example.reading.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class DegradeEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private final Log log;

    public DegradeEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(DegradeEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        log.info("DegradeEnvironmentPostProcessor running — checking infrastructure switches");

        Map<String, Object> additionalProperties = new HashMap<>();
        String currentExcludes = environment.getProperty("spring.autoconfigure.exclude", "");

        boolean esEnabled = environment.getProperty("app.elasticsearch.enabled", Boolean.class, true);
        if (!esEnabled) {
            log.warn("app.elasticsearch.enabled is false. Disabling ALL Elasticsearch auto-configuration...");
            currentExcludes += (currentExcludes.isEmpty() ? "" : ",") +
                    // Data & repositories
                    "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration," +
                    "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration," +
                    "org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration," +
                    // Clients
                    "org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration," +
                    "org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration," +
                    "org.springframework.boot.autoconfigure.elasticsearch.ReactiveElasticsearchClientAutoConfiguration";
        }

        boolean redisEnabled = environment.getProperty("app.redis.enabled", Boolean.class, true);
        if (!redisEnabled) {
            log.warn("app.redis.enabled is false. Disabling ALL Redis auto-configuration...");
            currentExcludes += (currentExcludes.isEmpty() ? "" : ",") +
                    "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                    "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration," +
                    "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration";
        }

        if (!currentExcludes.isEmpty()) {
            additionalProperties.put("spring.autoconfigure.exclude", currentExcludes);
            environment.getPropertySources().addFirst(new MapPropertySource("degradeProperties", additionalProperties));
        }
    }
}
