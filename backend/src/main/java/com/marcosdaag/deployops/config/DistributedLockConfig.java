package com.marcosdaag.deployops.config;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "50s")
public class DistributedLockConfig {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLockConfig.class);

    @Bean
    public LockProvider lockProvider(
            ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider,
            @Value("${app.redis.enabled:false}") boolean redisEnabled
    ) {
        InMemoryLockProvider fallbackProvider = new InMemoryLockProvider();
        RedisConnectionFactory connectionFactory = redisConnectionFactoryProvider.getIfAvailable();

        if (redisEnabled && connectionFactory != null) {
            logger.info("Coordinacion distribuida ShedLock activada sobre Redis (namespace: deployops).");
            RedisLockProvider redisLockProvider = new RedisLockProvider(connectionFactory, "deployops");
            return new ResilientLockProvider(redisLockProvider, fallbackProvider);
        }

        logger.info("Coordinacion ShedLock operando en modo autonomo en memoria (nodo unico / Fail-Open).");
        return fallbackProvider;
    }

    /**
     * Decorador tolerante a fallos (Fail-Open): delega la adquisicion del candado atomico a Redis
     * y, ante una caida transitoria de red del cluster Redis, cae limpiamente al candado local
     * de la JVM para no interrumpir los ciclos de monitoreo.
     */
    static final class ResilientLockProvider implements LockProvider {

        private final LockProvider primary;
        private final LockProvider fallback;

        ResilientLockProvider(LockProvider primary, LockProvider fallback) {
            this.primary = primary;
            this.fallback = fallback;
        }

        @Override
        public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
            try {
                return primary.lock(lockConfiguration);
            } catch (Exception ex) {
                logger.warn(
                        "Fallo transitorio al adquirir candado ShedLock '{}' en Redis ({}). Activando respaldo local.",
                        lockConfiguration.getName(),
                        ex.getMessage()
                );
                return fallback.lock(lockConfiguration);
            }
        }
    }

    /**
     * Proveedor de bloqueo atomico en memoria para ejecucion en nodo unico, pruebas unitarias
     * o contingencia ante indisponibilidad de Redis.
     */
    static final class InMemoryLockProvider implements LockProvider {

        private final ConcurrentHashMap<String, Instant> activeLocks = new ConcurrentHashMap<>();

        @Override
        public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
            Instant now = Instant.now();
            String name = lockConfiguration.getName();
            Instant lockAtMostUntil = lockConfiguration.getLockAtMostUntil();

            boolean[] acquired = {false};
            activeLocks.compute(name, (key, lockedUntil) -> {
                if (lockedUntil == null || !now.isBefore(lockedUntil)) {
                    acquired[0] = true;
                    return lockAtMostUntil;
                }
                return lockedUntil;
            });

            if (!acquired[0]) {
                return Optional.empty();
            }

            return Optional.of(() -> {
                Instant unlockTime = Instant.now();
                Instant lockAtLeastUntil = lockConfiguration.getLockAtLeastUntil();
                Instant effectiveUntil = unlockTime.isBefore(lockAtLeastUntil) ? lockAtLeastUntil : unlockTime;
                activeLocks.put(name, effectiveUntil);
            });
        }
    }
}
