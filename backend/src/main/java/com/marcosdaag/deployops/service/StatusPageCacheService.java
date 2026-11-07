package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.StatusPageDTO;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.UserEntity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class StatusPageCacheService {

    private static final Logger logger = LoggerFactory.getLogger(StatusPageCacheService.class);
    static final String KEY_PREFIX = "deployops:status_page:";

    private final StringRedisTemplate redisTemplate;
    private final boolean redisEnabled;
    private final Duration ttl;
    private final JsonMapper jsonMapper;

    private final Counter hitCounter;
    private final Counter missCounter;
    private final Counter evictionCounter;

    private final AtomicLong localHits = new AtomicLong(0);
    private final AtomicLong localMisses = new AtomicLong(0);
    private final AtomicLong localEvictions = new AtomicLong(0);

    @Autowired
    public StatusPageCacheService(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            @Value("${app.redis.enabled:false}") boolean redisEnabled,
            @Value("${app.status-cache.ttl-seconds:60}") long ttlSeconds
    ) {
        this(
                redisTemplateProvider.getIfAvailable(),
                meterRegistryProvider.getIfAvailable(),
                redisEnabled,
                ttlSeconds
        );
    }

    public StatusPageCacheService(
            StringRedisTemplate redisTemplate,
            MeterRegistry meterRegistry,
            boolean redisEnabled,
            long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.redisEnabled = redisEnabled && redisTemplate != null;
        this.ttl = Duration.ofSeconds(Math.max(5L, ttlSeconds));
        this.jsonMapper = JsonMapper.builder().build();

        if (meterRegistry != null) {
            this.hitCounter = Counter.builder("deployops.cache.status_page.hits")
                    .description("Lecturas servidas desde cache L2 Redis para Status Page")
                    .register(meterRegistry);
            this.missCounter = Counter.builder("deployops.cache.status_page.misses")
                    .description("Lecturas de Status Page que requirieron consulta a PostgreSQL")
                    .register(meterRegistry);
            this.evictionCounter = Counter.builder("deployops.cache.status_page.evictions")
                    .description("Invalidaciones reactivas ejecutadas sobre cache L2 de Status Page")
                    .register(meterRegistry);
        } else {
            this.hitCounter = null;
            this.missCounter = null;
            this.evictionCounter = null;
        }
    }

    public Optional<StatusPageDTO> get(String slug) {
        if (!redisEnabled) {
            return Optional.empty();
        }

        String key = buildKey(slug);
        try {
            String cachedJson = redisTemplate.opsForValue().get(key);
            if (cachedJson != null && !cachedJson.isBlank()) {
                StatusPageDTO dto = jsonMapper.readValue(cachedJson, StatusPageDTO.class);
                recordHit();
                return Optional.of(dto);
            }
            recordMiss();
            return Optional.empty();
        } catch (Exception ex) {
            logger.warn("Fallo al leer cache L2 en Redis para clave '{}' ({}). Operando en modo Fail-Open.", key, ex.getMessage());
            recordMiss();
            return Optional.empty();
        }
    }

    public void put(String slug, StatusPageDTO dto) {
        if (!redisEnabled || dto == null) {
            return;
        }

        String key = buildKey(slug);
        try {
            String payload = jsonMapper.writeValueAsString(dto);
            redisTemplate.opsForValue().set(key, payload, ttl);
        } catch (Exception ex) {
            logger.warn("Fallo al escribir cache L2 en Redis para clave '{}' ({}). Se omite escritura.", key, ex.getMessage());
        }
    }

    public void evictBySlug(String slug) {
        if (!redisEnabled) {
            return;
        }

        String key = buildKey(slug);
        try {
            Boolean deleted = redisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                logger.debug("Cache L2 invalidada reactivamente para Status Page '{}'.", key);
            }
            recordEviction();
        } catch (Exception ex) {
            logger.warn("Fallo al invalidar cache L2 en Redis para clave '{}' ({}).", key, ex.getMessage());
        }
    }

    public void evictForService(ServiceEntity service) {
        if (!redisEnabled || service == null) {
            return;
        }

        UserEntity owner = service.getUser();
        if (owner == null) {
            evictBySlug("demo");
        } else if (owner.getPublicSlug() != null && !owner.getPublicSlug().isBlank()) {
            evictBySlug(owner.getPublicSlug());
        }
    }

    public boolean isRedisCacheActive() {
        return redisEnabled;
    }

    public long getHits() {
        return localHits.get();
    }

    public long getMisses() {
        return localMisses.get();
    }

    public long getEvictions() {
        return localEvictions.get();
    }

    static String normalizeSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return "demo";
        }
        return slug.trim().toLowerCase();
    }

    private String buildKey(String slug) {
        return KEY_PREFIX + normalizeSlug(slug);
    }

    private void recordHit() {
        localHits.incrementAndGet();
        if (hitCounter != null) {
            hitCounter.increment();
        }
    }

    private void recordMiss() {
        localMisses.incrementAndGet();
        if (missCounter != null) {
            missCounter.increment();
        }
    }

    private void recordEviction() {
        localEvictions.incrementAndGet();
        if (evictionCounter != null) {
            evictionCounter.increment();
        }
    }
}
