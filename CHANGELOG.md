# Registro de Cambios (Changelog)

Todos los cambios relevantes de **DeployOps** se documentan en este archivo siguiendo el estándar [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y [Versionado Semántico](https://semver.org/lang/es/).

---

## [Unreleased] — DeployOps v2.0 Enterprise (Fases 1 y 2 Completadas)

### Añadido (Fase 2 — Motor Orientado a Eventos, Transactional Outbox, RabbitMQ 3.13, Webhooks AES/HMAC, DLQ y Chaos API)
- **Patrón Transactional Outbox (`OutboxEventEntity` y `outbox_events`):** Registro atómico inmutable de eventos de incidente (`INCIDENT_OPENED`, `INCIDENT_RESOLVED`) dentro de la misma transacción SQL `@Transactional` de `EndpointManagerService`, eliminando el problema de *Dual-Write*.
- **Relay asíncrono y rescate de zombies (`OutboxRelayService`):** Proceso periódico protegido por ShedLock (`outboxRelaySweepLock`) que publica lotes pendientes en RabbitMQ (o Virtual Threads si RabbitMQ no está habilitado), con *Zombie Lock Reaper* que rescata eventos en `PROCESSING` abandonados por más de 2 minutos y purga programada a los 7 días.
- **Topología AMQP (`RabbitMqTopologyConfig`):** Declaración de Topic Exchange `deployops.incidents`, colas `webhooks.dispatch` y `audit.telemetry`, Dead Letter Exchange `deployops.incidents.dlx` y Dead Letter Queue `webhooks.dlq`.
- **Cifrado en reposo AES-256-GCM (`SecretCryptoConverter`):** Cifrado transparente de secretos `whsec_...` con IV aleatorio de 12 bytes y etiqueta de 128 bits; enmascaramiento permanente en lecturas REST (`whsec_••••••••4f9a`).
- **Firma criptográfica HMAC-SHA256 Anti-Replay (`HmacSignatureService`):** Generación de secretos seguros y firma de cabecera `X-DeployOps-Signature-256` sobre `timestampEpochSeconds + "." + rawJsonBody` con ventana de tolerancia de 300 segundos.
- **Entrega de webhooks con Backoff Exponencial y DLQ (`WebhookDeliveryService`):** Validación Anti-SSRF previa a cada intento (Anti-DNS-Rebinding), tope de lectura de 4 KB, 3 intentos con backoff (2s, 8s, 30s) + jitter aleatorio, desvío a DLQ y auditoría en `webhook_delivery_failures`.
- **Adaptadores de payload multiformato (`WebhookPayloadFormatter`):** Formateo automático para Slack Block Kit, Discord Embeds enriquecidos y JSON canónico.
- **Sandbox y Simulador de Caos SRE (`ChaosSimulationService` y `ChaosSimulatorController`):** Estado de receptor virtual particionado por usuario (`200`, `503`, `504`, `401`) y disparo de simulaciones para pruebas de contingencia.
- **Nuevos controladores REST:** `WebhookController` (CRUD, rotación de secreto, test ping, auditoría DLQ y reintento manual) y `DistributedTelemetryController` (`/api/distributed/overview`).
- **Gobernanza documental de Fase 2:** Registro de `ADR 12` y `ADR 13` en `docs/DECISIONS.md`, expansión del ERD a 7 tablas y reglas 3.8-3.12 en `docs/DOMAIN_AND_RULES.md`, diagrama de secuencia AMQP en `docs/ARCHITECTURE.md`, creación de `docs/WEBHOOKS_AND_SECURITY.md` y `docs/API_REFERENCE.md`.

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
