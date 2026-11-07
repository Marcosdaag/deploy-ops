# Arquitectura del Sistema

Este documento describe la estructura técnica y de componentes de **DeployOps**, detallando las responsabilidades de cada capa, la coordinación distribuida multi-nodo y los flujos de comunicación reactiva.

---

## 1. Contexto del Sistema (C4 Model — Evolución v2.0 Fase 1)

El siguiente diagrama ilustra cómo interactúan los operadores SRE, los visitantes públicos y la infraestructura de datos y coordinación con el clúster de la plataforma:

```mermaid
flowchart TD
    classDef person fill:#08427b,stroke:#052e56,color:#fff,rx:20,ry:20
    classDef system fill:#1168bd,stroke:#0b4884,color:#fff
    classDef infra fill:#0f766e,stroke:#115e59,color:#fff
    classDef external fill:#64748b,stroke:#475569,color:#fff

    Admin["Operador SRE / Desarrollador"]:::person
    PublicUser["Usuario Final / Visitante"]:::person

    LB["Balanceador / Edge Proxy"]:::external
    DeployOps["Clúster DeployOps Core<br/>(Angular 20.3 + Nodos Spring Boot 4.1.1 / Loom)"]:::system
    Redis["Redis 7.2<br/>(ShedLock Leader Election + Caché L2 + Rate Limit Global)"]:::infra
    Postgres["PostgreSQL 16<br/>(Persistencia ACID de Cuentas, Servicios e Incidentes)"]:::infra
    APIs["APIs y Microservicios Monitoreados<br/>(Endpoints HTTP/TLS externos)"]:::external

    Admin -->|Gestiona endpoints y monitorea telemetría| LB
    PublicUser -->|Consulta Status Page pública /status/:slug| LB
    LB --> DeployOps
    DeployOps -->|Adquiere candado ShedLock, evalúa cuotas y lee/invalida Caché L2| Redis
    DeployOps -->|Persiste telemetría, incidentes y consultas SLA O(1)| Postgres
    DeployOps -->|Health Checks concurrentes con Virtual Threads y escudo Anti-SSRF| APIs
```

---

## 2. Arquitectura Interna del Backend (Spring Boot 4.1.1 / Java 21)

El backend implementa una arquitectura en capas estrictas con soporte híbrido para despliegue distribuido multi-nodo y degradación elegante (*Fail-Open*) en entornos de nodo único:

```mermaid
flowchart TD
    subgraph Web ["Capa Web y Perímetro de Seguridad"]
        RLF["RateLimitingFilter + RateLimiterService<br/>(Redis INCR Atómico / Fallback Local)"]
        JWTF["JwtAuthenticationFilter<br/>(Validación JWT + OAuth2 Verificado)"]
        AC["AuthController (/api/auth)"]
        EC["EndpointController (/api/endpoints)"]
        SPC["StatusPageController (/api/status)"]
        UVal["UrlSecurityValidator<br/>(Escudo Perimetral Anti-SSRF)"]
        GEH["GlobalExceptionHandler<br/>(Sanitización HTTP 500)"]
    end

    subgraph Core ["Capa de Negocio y Caché Distribuida"]
        AuthS["AuthService<br/>(BCrypt, Google OAuth2, Modo Demo)"]
        EMS["EndpointManagerService<br/>(CRUD, Multi-Tenancy y Máquina de 5 Estados)"]
        MS["MetricsService<br/>(Uptime SLA 30d sin N+1)"]
        MHS["MetricsHistoryService<br/>(Series 6h-30d y Purga con @SchedulerLock)"]
        SPS["StatusPageService<br/>(Agregación Pública por Slug)"]
        SPCS["StatusPageCacheService<br/>(Caché L2 Redis TTL 60s + Evicción Reactiva)"]
        RS["ReportService (Exportación CSV)"]
        SSEN["SseNotificationService<br/>(Canal Público SSE Aislado)"]
        EAS["EmailAlertService (Alertas SMTP)"]
    end

    subgraph Background ["Motor Asíncrono Coordinado (Loom + ShedLock)"]
        DLC["DistributedLockConfig<br/>(ResilientLockProvider: Redis + Fallback JVM)"]
        SCH["MonitoringScheduler<br/>(@Scheduled 60s + @SchedulerLock)"]
        VT["VirtualThreadExecutor<br/>(Executors.newVirtualThreadPerTaskExecutor)"]
        HM["HttpMonitorService<br/>(HTTP Check Monotónico + Auditoría TLS X.509)"]
    end

    subgraph Data ["Persistencia y Memoria Distribuida"]
        RED[("Redis 7.2")]
        PG[("PostgreSQL 16<br/>users, services, incidents, ping_logs")]
    end

    RLF --> RED
    RLF --> JWTF
    JWTF --> AC & EC & SPC
    EC --> UVal & EMS & MHS & RS & SSEN
    AC --> AuthS
    SPC --> SPS
    SPS --> SPCS
    SPCS --> RED
    DLC --> RED
    SCH --> DLC
    SCH --> VT
    VT --> HM
    HM --> EMS
    EMS -->|Cambio de estado o visibilidad| SPCS
    EMS --> EAS & SSEN & MS & PG
    MHS --> PG
    SPS --> PG
    AuthS --> PG
```

---

## 3. Flujo del Ciclo de Monitoreo Coordinado Multi-Nodo

```mermaid
sequenceDiagram
    autonumber
    participant NA as Nodo A (Spring Boot)
    participant NB as Nodo B (Spring Boot)
    participant R as Redis 7 (ShedLock + L2)
    participant VT as VirtualThreadExecutor
    participant E as Endpoint Externo
    participant M as EndpointManagerService
    participant DB as PostgreSQL 16

    par Disparo programado cada 60s
        NA->>R: SET deployops:monitoringSweepLock NX PX 50000
        R-->>NA: OK (Liderazgo otorgado al Nodo A)
    and
        NB->>R: SET deployops:monitoringSweepLock NX PX 50000
        R-->>NB: NIL (Nodo B omite barrido sin duplicar pings)
    end

    NA->>DB: Recupera todos los servicios registrados
    loop En paralelo por cada servicio (Java 21 Virtual Thread)
        NA->>VT: Ejecuta sondeo HTTP + inspección SSL X.509
        VT->>E: Petición sin redirecciones (timeouts 3s/5s)
        E-->>VT: Respuesta HTTP / Timeout
        VT->>M: processPingResult(service, pingResult)
        alt El estado del servicio cambia (ej. HEALTHY -> DEGRADED o DOWN)
            M->>DB: Persiste nuevo estado (y abre/cierra IncidentEntity si corresponde)
            M->>R: DEL deployops:status_page:{slug} (Invalidación reactiva inmediata)
        else El estado se mantiene estable
            M->>DB: Actualiza latencia y registra PingLogEntity
        end
    end
```

---

## 4. Flujo de Lectura e Invalidación Reactiva en Status Page (Caché L2)

```mermaid
sequenceDiagram
    autonumber
    participant C as Visitante (/status/:slug)
    participant SPS as StatusPageService
    participant Cache as StatusPageCacheService
    participant R as Redis 7.2
    participant DB as PostgreSQL 16

    C->>SPS: GET /api/status/acme-cloud
    SPS->>Cache: get("acme-cloud")
    Cache->>R: GET deployops:status_page:acme-cloud
    alt Cache Hit (Clave vigente en RAM)
        R-->>Cache: JSON StatusPageDTO
        Cache-->>SPS: Optional.of(StatusPageDTO) [Incrementa hits]
        SPS-->>C: HTTP 200 OK (~1.5 ms, 0 consultas SQL)
    else Cache Miss (Expirada o recién invalidada por incidente)
        R-->>Cache: nil [Incrementa misses]
        SPS->>DB: Consulta servicios públicos + SLA O(1) + últimos 10 incidentes
        DB-->>SPS: Entidades consolidadas
        SPS->>Cache: put("acme-cloud", dto) [SET EX 60s]
        SPS-->>C: HTTP 200 OK (Datos frescos reconstruidos)
    end
```


---

## 5. Ciclo de Vida del Transactional Outbox y Topologia AMQP hacia Dead Letter Queue

```mermaid
sequenceDiagram
    autonumber
    participant EMS as EndpointManagerService
    participant DB as PostgreSQL (ACID)
    participant Relay as OutboxRelayService
    participant RMQ as RabbitMQ (Exchange: deployops.incidents)
    participant Cons as WebhookDispatchConsumer
    participant WDS as WebhookDeliveryService
    participant Ext as Servidor Cliente (Slack / Discord / API)
    participant DLQ as RabbitMQ (DLQ: webhooks.dlq)

    Note over EMS,DB: 1. Transaccion Atomica ACID Unica
    EMS->>DB: INSERT incidents (ONGOING) + UPDATE services (DOWN)
    EMS->>DB: INSERT outbox_events (PENDING, eventId, payloadJson)

    Note over Relay,RMQ: 2. Despacho Desacoplado con Exclusion Mutua
    loop Cada 5 segundos (@SchedulerLock)
        Relay->>DB: SELECT PENDING LIMIT 50 -> UPDATE status=PROCESSING, lockedAt=now()
        alt RabbitMQ habilitado
            Relay->>RMQ: BasicPublish(exchange="deployops.incidents", key="incident.down")
            RMQ->>Cons: Encolado en webhooks.dispatch
            Cons->>WDS: deliver(event, webhookEndpoint)
        else Fallback en memoria / Render (Virtual Threads)
            Relay->>WDS: deliver(event, webhookEndpoint)
        end
        Relay->>DB: UPDATE outbox_events SET status=PROCESSED, processedAt=now()
    end

    Note over WDS,Ext: 3. Seguridad Perimetral y Entrega Criptografica
    loop Maximo 3 Intentos (Exponential Backoff + Jitter)
        WDS->>WDS: UrlSecurityValidator.validate(targetUrl) [Anti-DNS-Rebinding Pre-Vuelo]
        WDS->>WDS: Descifra secreto AES-256-GCM y calcula X-DeployOps-Signature-256 (HMAC-SHA256)
        WDS->>Ext: POST targetUrl (sin redirecciones, tope 4 KB respuesta)
        alt Respuesta HTTP 2xx
            Ext-->>WDS: 200 OK
            WDS->>DB: UPDATE webhook_endpoints SET last_delivery_status='200 OK'
        else Timeout o HTTP 5xx
            Ext-->>WDS: 500 / 503 / Timeout
            Note over WDS: Aguarda Backoff (2s, 8s, 30s) + Jitter (+/-20%)
        end
    end

    Note over WDS,DLQ: 4. Agotamiento de 3 Intentos y Auditoria Dead Letter
    opt Si fallaron los 3 intentos
        WDS->>DLQ: Desvia mensaje a webhooks.dlq (DLX)
        WDS->>DB: INSERT webhook_delivery_failures (DEAD_LETTER, attempt_count=3)
        WDS->>DB: UPDATE webhook_endpoints SET last_delivery_status='Fallo HTTP 503'
    end
```
