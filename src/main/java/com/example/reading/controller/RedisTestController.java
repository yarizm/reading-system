package com.example.reading.controller;

import com.example.reading.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/redis")
public class RedisTestController {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping
    public Result<String> testConnection() {
        try {
            // 1. 尝试写入数据
            redisTemplate.opsForValue().set("test_key", "Hello Redis!");

            // 2. 尝试读取数据
            String value = redisTemplate.opsForValue().get("test_key");

            // 3. 验证
            if ("Hello Redis!".equals(value)) {
                return Result.success("连接成功！读取到的值：" + value);
            } else {
                return Result.error("500", "连接通了，但读写数据不一致");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("500", "Redis 连接失败: " + e.getMessage());
        }
    }
}