package com.marcosdaag.deployops.config;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DistributedLockConfigTest {

    @Test
    @DisplayName("InMemoryLockProvider garantiza exclusion mutua entre hilos concurrentes y libera al expirar")
    void inMemoryLockProvider_shouldEnforceMutualExclusion() {
        DistributedLockConfig.InMemoryLockProvider provider = new DistributedLockConfig.InMemoryLockProvider();

        LockConfiguration lockConfig = new LockConfiguration(
                Instant.now(),
                "monitoringSweepLock",
                Duration.ofSeconds(30),
                Duration.ZERO
        );

        Optional<SimpleLock> firstAttempt = provider.lock(lockConfig);
        Optional<SimpleLock> secondAttemptWhileLocked = provider.lock(lockConfig);

        assertTrue(firstAttempt.isPresent(), "El primer nodo debe adquirir el candado");
        assertTrue(secondAttemptWhileLocked.isEmpty(), "El segundo nodo debe recibir vacio mientras el candado este activo");

        firstAttempt.get().unlock();

        LockConfiguration nextCycleConfig = new LockConfiguration(
                Instant.now().plusMillis(5),
                "monitoringSweepLock",
                Duration.ofSeconds(30),
                Duration.ZERO
        );
        Optional<SimpleLock> afterUnlock = provider.lock(nextCycleConfig);
        assertTrue(afterUnlock.isPresent(), "Tras liberar el candado debe poder adquirirse en el siguiente ciclo");
    }

    @Test
    @DisplayName("ResilientLockProvider activa respaldo en memoria si el proveedor Redis lanza excepcion")
    void resilientLockProvider_shouldFallbackToLocalWhenPrimaryFails() {
        LockProvider failingRedisProvider = config -> {
            throw new IllegalStateException("Redis cluster unreachable");
        };
        DistributedLockConfig.InMemoryLockProvider fallback = new DistributedLockConfig.InMemoryLockProvider();
        DistributedLockConfig.ResilientLockProvider resilient = new DistributedLockConfig.ResilientLockProvider(
                failingRedisProvider,
                fallback
        );

        LockConfiguration lockConfig = new LockConfiguration(
                Instant.now(),
                "purgeOldPingLogsLock",
                Duration.ofSeconds(30),
                Duration.ZERO
        );

        Optional<SimpleLock> lock = assertDoesNotThrow(() -> resilient.lock(lockConfig));
        assertTrue(lock.isPresent(), "El respaldo local debe otorgar el candado ante fallo de Redis");
    }
}
