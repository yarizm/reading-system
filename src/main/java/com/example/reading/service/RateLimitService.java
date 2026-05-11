package com.example.reading.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RateLimitService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String PREFIX = "rateLimit:";

    public boolean isAllowed(String key, int limit, int seconds) {
        String redisKey = PREFIX + key;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1) {
            redisTemplate.expire(redisKey, seconds, TimeUnit.SECONDS);
        }
        return count != null && count <= limit;
    }

    public void reset(String key) {
        redisTemplate.delete(PREFIX + key);
    }
}
