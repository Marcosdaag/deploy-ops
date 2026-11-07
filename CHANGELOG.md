# Registro de Cambios (Changelog)

Todos los cambios relevantes de **DeployOps** se documentan en este archivo siguiendo el estándar [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y [Versionado Semántico](https://semver.org/lang/es/).

---

## [Unreleased] — DeployOps v2.0 Enterprise (Fase 1 Completada)

### Añadido (Fase 1 — Infraestructura Distribuida, Redis 7, ShedLock y Caché L2)
- **Topología local multi-servicio (`docker-compose.yml`):** Incorporación de `redis:7.2-alpine` (persistencia AOF en puerto `6379`) y `rabbitmq:3.13-management-alpine` (puertos `5672` y `15672`) junto a `postgres:16-alpine`, todos con `healthcheck` nativo.
- **Coordinación distribuida multi-nodo (`DistributedLockConfig` + `ShedLock`):** Exclusión mutua atómica sobre Redis (`@SchedulerLock`) en `MonitoringScheduler.checkAllEndpoints` y `MetricsHistoryService.purgeOldLogs`, acompañada de `ResilientLockProvider` e `InMemoryLockProvider` para degradación automática (*Fail-Open*) ante caídas de red o despliegues de nodo único.
- **Caché L2 de Status Page con invalidación reactiva (`StatusPageCacheService`):** Almacenamiento de `StatusPageDTO` en Redis con `TTL` configurable (`60s` por defecto), serialización Jackson 3 (`JsonMapper`), métricas Micrometer (`hits`, `misses`, `evictions`) e invalidación inmediata disparada por transiciones de estado en `EndpointManagerService`, cambios de visibilidad y actualización de `publicSlug`.
- **Rate Limiting distribuido (`RateLimiterService`):** Evaluación global de ventanas de peticiones mediante incrementos atómicos en Redis (`INCR` + `PEXPIRE`) con respaldo transparente en `ConcurrentHashMap` local.
- **Gobernanza y Arquitectura Documental:** Creación de `SECURITY.md`, `CHANGELOG.md` y registro de `ADR 10` y `ADR 11` en `docs/DECISIONS.md`.

---

## [1.0.0] — 2026-11-07

### Añadido
- Motor de monitoreo HTTP/HTTPS concurrente basado en **Java 21 Virtual Threads** (`Project Loom`) y **Spring Boot 4.1.1**, con medición monotónica (`System.nanoTime()`) e inspección de certificados TLS/SSL (`X509Certificate`).
- Máquina de estados finitos de 5 estados (`PENDING`, `HEALTHY`, `DEGRADED`, `DOWN`, `RECOVERING`) con política 3/3 de confirmación de caída y 2/2 de recuperación.
- Escudo perimetral Anti-SSRF (`UrlSecurityValidator`) respaldado por 44 pruebas unitarias contra subredes RFC 1918, loopback, enlace local, metadatos Cloud y puertos internos.
- Cálculo de disponibilidad SLA de 30 días en una única consulta JPQL sin problema N+1 (`MetricsService`), series históricas (`6h`, `24h`, `7d`, `30d`) y exportación CSV mensual.
- Interfaz SPA en **Angular 20.3** con **Signals**, esfera geodésica interactiva 3D en **Three.js r187** con contornos de Natural Earth, Libro Mayor SRE desacoplado, soporte completo de Modo Oscuro y Modo Claro (incluyendo Status Page pública `/status/:slug`) y consola `cURL` multilínea.
- **Modo Demo interactivo sin registro** (`POST /api/auth/demo`) con sembrado automático de endpoints de muestra y respaldo local sin conexión.
- Verificación criptográfica de tokens Google OAuth2 (`POST /api/auth/google`), aislamiento multi-tenant del canal reactivo SSE, sanitización de errores HTTP `500` y suite de **161 pruebas automatizadas** en integración continua (GitHub Actions).
