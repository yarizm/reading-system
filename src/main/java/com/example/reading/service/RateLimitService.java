package com.example.reading.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimitService {

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private static final String PREFIX = "rateLimit:";
    
    private final Map<String, LocalCount> localCache = new ConcurrentHashMap<>();

    private static class LocalCount {
        int count;
        long expireTime;
    }

    public boolean isAllowed(String key, int limit, int seconds) {
        if (redisTemplate != null) {
            try {
                String redisKey = PREFIX + key;
                Long count = redisTemplate.opsForValue().increment(redisKey);
                if (count != null && count == 1) {
                    redisTemplate.expire(redisKey, seconds, TimeUnit.SECONDS);
                }
                return count != null && count <= limit;
            } catch (Exception e) {
                // Fallback to local cache if Redis fails
            }
        }
        
        long now = System.currentTimeMillis();
        LocalCount lc = localCache.compute(key, (k, v) -> {
            if (v == null || now > v.expireTime) {
                LocalCount newLc = new LocalCount();
                newLc.count = 1;
                newLc.expireTime = now + seconds * 1000L;
                return newLc;
            } else {
                v.count++;
                return v;
            }
        });
        return lc.count <= limit;
    }

    public void reset(String key) {
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(PREFIX + key);
                return;
            } catch (Exception e) {
                // Ignore
            }
        }
        localCache.remove(key);
    }
}
