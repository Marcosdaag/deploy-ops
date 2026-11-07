package com.marcosdaag.deployops.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimiterService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterService.class);
    static final String KEY_PREFIX = "deployops:rl:";

    private static class RequestWindow {
        final long windowStart;
        final AtomicInteger counter;

        RequestWindow(long windowStart) {
            this.windowStart = windowStart;
            this.counter = new AtomicInteger(1);
        }
    }

    private final ConcurrentHashMap<String, RequestWindow> buckets = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final boolean redisEnabled;

    public RateLimiterService() {
        this.redisTemplate = null;
        this.redisEnabled = false;
    }

    @Autowired
    public RateLimiterService(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${app.redis.enabled:false}") boolean redisEnabled
    ) {
        this(redisTemplateProvider.getIfAvailable(), redisEnabled);
    }

    public RateLimiterService(StringRedisTemplate redisTemplate, boolean redisEnabled) {
        this.redisTemplate = redisTemplate;
        this.redisEnabled = redisEnabled && redisTemplate != null;
    }

    public boolean tryAcquire(String key, int maxRequests, long windowDurationMillis) {
        if (redisEnabled) {
            try {
                long bucketSlot = System.currentTimeMillis() / Math.max(1L, windowDurationMillis);
                String redisKey = KEY_PREFIX + key + ":" + bucketSlot;
                Long count = redisTemplate.opsForValue().increment(redisKey);
                if (count != null) {
                    if (count == 1L) {
                        redisTemplate.expire(redisKey, Duration.ofMillis(windowDurationMillis + 1000L));
                    }
                    return count <= maxRequests;
                }
            } catch (Exception ex) {
                logger.warn(
                        "Error al evaluar cuota distribuida en Redis para clave '{}' ({}). Aplicando respaldo local en memoria.",
                        key,
                        ex.getMessage()
                );
            }
        }
        return tryAcquireLocal(key, maxRequests, windowDurationMillis);
    }

    private boolean tryAcquireLocal(String key, int maxRequests, long windowDurationMillis) {
        long now = System.currentTimeMillis();

        buckets.compute(key, (k, current) -> {
            if (current == null || (now - current.windowStart) > windowDurationMillis) {
                return new RequestWindow(now);
            }
            current.counter.incrementAndGet();
            return current;
        });

        RequestWindow window = buckets.get(key);
        return window != null && window.counter.get() <= maxRequests;
    }

    @Scheduled(fixedRate = 300000)
    public void purgeExpiredBuckets() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry -> (now - entry.getValue().windowStart) > 120000);
    }

    public void reset() {
        buckets.clear();
    }

    public boolean isDistributedMode() {
        return redisEnabled;
    }
}
