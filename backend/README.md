# DeployOps — Backend Core

Motor distribuido de observabilidad activa y gestión de incidentes SRE desarrollado con **Java 21 LTS**, **Spring Boot 4.1.1**, **Virtual Threads (Project Loom)**, **PostgreSQL 16** y **Redis 7.2**.

## Capacidades Técnicas

- **Concurrencia con Hilos Virtuales:** Orquestación paralela mediante `Executors.newVirtualThreadPerTaskExecutor()` sin bloqueo de hilos del sistema operativo, medición monotónica con `System.nanoTime()` e inspección directa de certificados TLS (`X509Certificate`).
- **Coordinación Multi-Nodo (ShedLock + Redis 7):** Exclusión mutua distribuida (`@SchedulerLock`) sobre `MonitoringScheduler` y `MetricsHistoryService` con respaldo *Fail-Open* en memoria local (`ResilientLockProvider`) ante caídas de red o despliegues de nodo único.
- **Caché L2 con Invalidación Reactiva:** `StatusPageCacheService` acelera las consultas públicas a `/api/status/{slug}` con `TTL` de 60 segundos en Redis, métricas Micrometer (`hits`, `misses`, `evictions`) e invalidación instantánea ante transiciones de estado, cambios de visibilidad o actualización de `publicSlug`.
- **Rate Limiting Global:** `RateLimitingFilter` y `RateLimiterService` evalúan ventanas de peticiones de forma atómica en Redis (`INCR` + `PEXPIRE`) con conmutación automática a cubetas en memoria si Redis no está habilitado.
- **Escudo Defensivo Anti-SSRF:** `UrlSecurityValidator` bloquea redirecciones abiertas, subredes privadas RFC 1918, loopback, Carrier-Grade NAT, metadatos Cloud (`169.254.169.254`) y puertos internos críticos.
- **Máquina de Estados y SLA sin N+1:** Confirmación 3/3 para apertura de incidentes `ONGOING` y 2/2 para cierre `RESOLVED`, con cálculo de disponibilidad mensual en una sola consulta JPQL por lote.

## Requisitos Previos

- Java Development Kit (JDK) 21 LTS
- Docker y Docker Compose
- Maven 3.9+ (o wrapper `./mvnw` incluido)

## Comandos de Desarrollo

### 1. Levantar Infraestructura Local (PostgreSQL 16 + Redis 7.2 + RabbitMQ 3.13)
```bash
docker compose -f ../docker-compose.yml up -d
```

### 2. Ejecutar Suite de Pruebas Automatizadas
Ejecución de las **134 pruebas unitarias y de contexto** en JUnit 5 y Mockito:
```bash
./mvnw test
```

### 3. Iniciar la Aplicación con Coordinación Redis Activa
```bash
REDIS_ENABLED=true ./mvnw spring-boot:run
```
En PowerShell (Windows):
```powershell
$env:REDIS_ENABLED="true"; .\mvnw.cmd spring-boot:run
```
La API quedará disponible en `http://localhost:8080`.
