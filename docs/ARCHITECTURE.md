# Arquitectura del Sistema

Este documento describe la estructura técnica y de componentes de DeployOps, detallando las responsabilidades de cada capa, los flujos de comunicación y el procesamiento de monitoreo concurrente.

---

## 1. Contexto del Sistema (C4 Model)

El siguiente diagrama ilustra cómo interactúan los usuarios y los sistemas externos con el núcleo de la plataforma:

```mermaid
flowchart TD
    classDef person fill:#08427b,stroke:#052e56,color:#fff,rx:20,ry:20
    classDef system fill:#1168bd,stroke:#0b4884,color:#fff
    classDef external fill:#999999,stroke:#6b6b6b,color:#fff
    
    Admin["Administrador / Ingeniero de Software"]:::person
    PublicUser["Usuario Final / Visitante"]:::person
    
    DeployOps["DeployOps Platform<br/>(Angular 20 + Spring Boot 4 en Render)"]:::system
    
    APIs["APIs y Microservicios Monitoreados<br/>(Endpoints externos)"]:::external
    Supabase["PostgreSQL 16 en Supabase<br/>(Persistencia de estado e incidentes)"]:::external

    Admin -->|Gestiona monitores y visualiza metricas privadas| DeployOps
    PublicUser -->|Consulta Status Page publica y bitacora| DeployOps
    DeployOps -->|Health Checks concurrentes con timeouts estrictos| APIs
    DeployOps -->|Persiste telemetria, incidentes y cuentas| Supabase
```

---

## 2. Arquitectura Interna del Backend (Spring Boot 4 / Java 21)

El backend sigue una arquitectura monolítica en capas estrictas bajo principios de inyección por constructor, inmutabilidad y separación de responsabilidades:

```mermaid
flowchart TD
    subgraph Web ["Capa Web (REST, Seguridad y Control de Tasa)"]
        RLF["RateLimitingFilter<br/>(Token Bucket por IP)"]
        JWTF["JwtAuthenticationFilter<br/>(Validacion de Bearer Token)"]
        AC["AuthController<br/>(/api/auth)"]
        EC["EndpointController<br/>(/api/endpoints)"]
        SPC["StatusPageController<br/>(/api/status)"]
        UVal["UrlSecurityValidator<br/>(Escudo anti-SSRF)"]
        GEH["GlobalExceptionHandler<br/>(Manejo homogeneo de errores)"]
    end

    subgraph Core ["Capa de Servicios (Reglas de Negocio)"]
        AuthS["AuthService<br/>(Registro, Login, BCrypt)"]
        EMS["EndpointManagerService<br/>(CRUD + Maquina de Estados)"]
        MS["MetricsService<br/>(Uptime SLA 30d sin N+1)"]
        MHS["MetricsHistoryService<br/>(Series Temporales y Purga)"]
        SPS["StatusPageService<br/>(Agregacion Publica por Slug)"]
        RS["ReportService<br/>(Generacion de Reportes CSV)"]
        SSEN["SseNotificationService<br/>(Server-Sent Events)"]
        EAS["EmailAlertService<br/>(Despacho Async de Notificaciones)"]
    end

    subgraph Background ["Capa Asincrona Concurrente (Loom)"]
        SCH["MonitoringScheduler<br/>(@Scheduled fixedDelay=60s)"]
        VT["VirtualThreadExecutor<br/>(Executors.newVirtualThreadPerTaskExecutor)"]
        HM["HttpMonitorService<br/>(HTTP Check + SSL Certificate Audit)"]
    end

    subgraph Data ["Capa de Persistencia (Spring Data JPA)"]
        UR["UserRepository"]
        SR["ServiceRepository"]
        IR["IncidentRepository"]
        PLR["PingLogRepository"]
        UE[("users")]
        SE[("services")]
        IE[("incidents")]
        PLE[("ping_logs")]
    end

    RLF --> JWTF
    JWTF --> AC
    JWTF --> EC
    JWTF --> SPC
    EC --> UVal
    EC --> EMS
    EC --> MHS
    EC --> RS
    EC --> SSEN
    AC --> AuthS
    SPC --> SPS
    SCH --> VT
    VT --> HM
    HM --> EMS
    EMS --> EAS
    EMS --> SSEN
    EMS --> SR
    EMS --> IR
    EMS --> PLR
    EMS --> MS
    MHS --> PLR
    AuthS --> UR
    SPS --> SR
    SPS --> IR
    UR --> UE
    SR --> SE
    IR --> IE
    PLR --> PLE
```

### Responsabilidades por Capa

1. **Capa Web y Perímetro de Seguridad:**
   - `RateLimitingFilter`: Aplica límites de consumo de peticiones por segundo utilizando el algoritmo Token Bucket basado en la IP de origen.
   - `JwtAuthenticationFilter`: Extrae y valida el token JWT de las cabeceras `Authorization: Bearer`, poblando el contexto de seguridad de Spring Security.
   - `EndpointController`: Expone las operaciones CRUD, telemetría y reportes para usuarios autenticados y vistas públicas.
   - `UrlSecurityValidator`: Analiza y valida cada URL previo a su almacenamiento o invocación, descartando subredes privadas, loopback, metadatos cloud y puertos restringidos.
   - `GlobalExceptionHandler`: Centraliza las excepciones controladas (`ResourceNotFoundException`, `SecurityException`, `MethodArgumentNotValidException`) transformándolas en un contrato unificado `ErrorResponseDTO`.

2. **Capa de Negocio (Core Services):**
   - `EndpointManagerService`: Coordina el ciclo de vida de los servicios, el aislamiento por usuario (`scope=mine` vs `scope=showcase`) y la máquina de estados finitos.
   - `MetricsService`: Ejecuta el cálculo ponderado de disponibilidad (Uptime) de los últimos 30 días con precisión de segundos, resolviendo el problema de las N+1 queries.
   - `MetricsHistoryService`: Agrupa los puntos de latencia para gráficos temporales (24 horas, 7 días, 30 días) y ejecuta la purga de logs antiguos.
   - `StatusPageService`: Resuelve la información pública agregada asociada a un slug personalizado.
   - `ReportService`: Formula el consolidado mensual de disponibilidad y genera el archivo CSV descargable.

3. **Capa de Monitoreo Asíncrono:**
   - `MonitoringScheduler`: Despacha un ciclo concurrente cada 60 segundos sobre hilos virtuales (`Virtual Threads`), ejecutando todos los pings en paralelo sin bloquear los núcleos del sistema operativo.
   - `HttpMonitorService`: Cliente HTTP con timeouts estrictos (3 segundos para conexión, 5 segundos para lectura) que mide el tiempo transcurrido con `System.nanoTime()` y extrae los metadatos de expiración del certificado X.509.

4. **Capa de Persistencia:**
   - Interfaces de Spring Data JPA sobre PostgreSQL en Supabase, implementando consultas nativas y JPQL optimizadas para lectura de rangos temporales superpuestos.

---

## 3. Flujo del Ciclo de Monitoreo Concurrente

```mermaid
sequenceDiagram
    autonumber
    participant S as MonitoringScheduler
    participant VT as VirtualThreadExecutor
    participant H as HttpMonitorService
    participant E as Endpoint Externo
    participant M as EndpointManagerService
    participant DB as PostgreSQL (Supabase)
    participant SSE as SseNotificationService

    loop Cada 60 segundos (fixedDelay)
        S->>DB: Recupera todos los servicios activos
        DB-->>S: List de ServiceEntity
        
        loop Para cada servicio en paralelo (Virtual Thread)
            S->>VT: Despacha tarea asincrona
            VT->>H: ping(service)
            H->>E: HTTP GET / HEAD (timeouts 3s/5s)
            
            alt Respuesta satisfactoria (2xx / Expected)
                E-->>H: Codigo HTTP esperado
                H-->>VT: PingResult(isSuccess=true, latencyMs)
            else Fallo de conexion / Timeout / 5xx
                E-->>H: Excepcion o codigo inesperado
                H-->>VT: PingResult(isSuccess=false, latencyMs)
            end
            
            VT->>M: processPingResult(service, pingResult)
            Note over M: Aplica Maquina de Estados<br/>(PENDING, HEALTHY, DEGRADED, DOWN, RECOVERING)
            
            alt Acumula 3 fallos consecutivos
                M->>DB: Abre registro IncidentEntity (ONGOING)
            else Confirma 2 exitos tras caida
                M->>DB: Cierra IncidentEntity (RESOLVED)
            end
            
            M->>DB: Persiste estado, latencia y nuevo PingLog
        end
        
        S->>SSE: broadcastDashboardUpdate(metricas)
        Note over SSE: Emite evento reactivo a clientes conectados
    end
```
