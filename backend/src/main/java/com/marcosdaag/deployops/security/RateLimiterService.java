package com.marcosdaag.deployops.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimiterService {

    private static class RequestWindow {
        final long windowStart;
        final AtomicInteger counter;

        RequestWindow(long windowStart) {
            this.windowStart = windowStart;
            this.counter = new AtomicInteger(1);
        }
    }

    private final ConcurrentHashMap<String, RequestWindow> buckets = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int maxRequests, long windowDurationMillis) {
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
}
