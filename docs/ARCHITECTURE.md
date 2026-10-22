# Arquitectura del Sistema

Este documento describe cómo está estructurado **DeployOps** a nivel de código y componentes, detallando las responsabilidades de cada capa, el flujo de comunicación y el procesamiento de monitoreo.

---

## 1. C4 Model - Contexto del Sistema

El siguiente diagrama ilustra cómo interactúan los usuarios y los sistemas externos con el núcleo de la plataforma:

```mermaid
flowchart TD
    classDef person fill:#08427b,stroke:#052e56,color:#fff,rx:20,ry:20
    classDef system fill:#1168bd,stroke:#0b4884,color:#fff
    classDef external fill:#999999,stroke:#6b6b6b,color:#fff
    
    Admin["👤 Administrador IT / Desarrollador"]:::person
    
    DeployOps["⚙️ DeployOps Platform<br/>(Angular + Spring Boot en Docker)"]:::system
    
    APIs["🌐 APIs y Microservicios Externos<br/>(Endpoints vigilados)"]:::external
    Supabase["🐘 Supabase PostgreSQL<br/>(Persistencia de estado e incidentes)"]:::external

    Admin -->|Consulta estado y administra endpoints| DeployOps
    DeployOps -->|Health Checks periódicos GET con timeouts| APIs
    DeployOps -->|Persiste servicios, métricas e incidentes| Supabase
```

---

## 2. Arquitectura Interna del Backend (Spring Boot 4 / Java 21)

El backend sigue una arquitectura monolítica en capas estrictas bajo principios de **Inyección por Constructor**, inmutabilidad y separación de responsabilidades:

```mermaid
flowchart TD
    subgraph Web ["🌐 Capa Web (REST & Seguridad)"]
        EC["EndpointController<br/>(/api/endpoints)"]
        UVal["UrlSecurityValidator<br/>(Escudo anti-SSRF)"]
        GEH["GlobalExceptionHandler<br/>(HTTP 400, 404, 500)"]
    end

    subgraph Core ["🧠 Capa de Servicios (Reglas de Negocio)"]
        EMS["EndpointManagerService<br/>(CRUD + Máquina de Estados)"]
        MS["MetricsService<br/>(Uptime % en segundos sin N+1)"]
    end

    subgraph Background ["⏱️ Vigilante en Segundo Plano"]
        SCH["MonitoringScheduler<br/>(@Scheduled fixedDelay=60s)"]
        HM["HttpMonitorService<br/>(RestTemplate con Timeouts + nanoTime)"]
    end

    subgraph Data ["💾 Capa de Persistencia (Spring Data JPA)"]
        SR["ServiceRepository"]
        IR["IncidentRepository"]
        SE[("services<br/>(ServiceEntity)")]
        IE[("incidents<br/>(IncidentEntity)")]
    end

    EC -->|Valida URLs| UVal
    EC -->|Delega operaciones| EMS
    SCH -->|Ejecuta Pings| HM
    SCH -->|Envía PingResult| EMS
    EMS -->|Calcula Uptime| MS
    EMS -->|Persiste estado| SR
    EMS -->|Abre/Cierra incidentes| IR
    MS -->|Consulta en lote| IR
    SR --> SE
    IR --> IE
```

### Responsabilidades por Capa:
1. **Capa Web:**
   * `EndpointController`: Expone las rutas REST, recibe `EndpointCreateDTO` validado con Jakarta Validation (`@Valid`) y retorna `EndpointResponseDTO` (nunca expone entidades JPA directamente).
   * `UrlSecurityValidator`: Protege contra ataques SSRF bloqueando `localhost`, `127.0.0.1`, direcciones de loopback, IPs privadas y metadatos Cloud.
   * `GlobalExceptionHandler`: Centraliza las respuestas de error en un formato homogéneo (`ErrorResponseDTO`), ocultando detalles sensibles de base de datos en errores 500.
2. **Capa de Servicios (Core):**
   * `EndpointManagerService`: Orquesta el alta de servicios (inicializados en `PENDING`), el borrado seguro en cascada y la transición de estados tolerante a fallos.
   * `MetricsService`: Calcula la disponibilidad porcentual de los últimos 30 días con precisión de segundos, resolviendo el problema de las **N+1 queries** mediante consultas en lote.
3. **Capa Asíncrona / Scheduler:**
   * `MonitoringScheduler`: Tarea programada que ejecuta los checks con `fixedDelay = 60000` y aislamiento de excepciones por cada servicio.
   * `HttpMonitorService`: Cliente HTTP con `connectTimeout` de 3s y `readTimeout` de 5s, usando `Void.class` para no sobrecargar la memoria RAM con cuerpos pesados.
4. **Capa de Persistencia:**
   * `ServiceRepository` e `IncidentRepository`: Interfaces JPA sobre PostgreSQL (Supabase en producción / Docker local), con consultas JPQL optimizadas para solapamiento de ventanas temporales.

---

## 3. Flujo del Ciclo de Monitoreo

El ciclo de chequeo continuo funciona de la siguiente manera:

```mermaid
sequenceDiagram
    autonumber
    participant S as MonitoringScheduler
    participant H as HttpMonitorService
    participant E as Endpoint externo
    participant M as EndpointManagerService
    participant DB as PostgreSQL (Supabase)

    loop Cada 60 segundos (fixedDelay)
        S->>DB: Obtiene lista de servicios activos
        DB-->>S: Lista de ServiceEntity
        
        loop Por cada servicio
            S->>H: ping(url)
            H->>E: HTTP GET (timeout 3s/5s)
            
            alt Responde 2xx
                E-->>H: 200 OK
                H-->>S: PingResult(isAlive=true, latencyMs)
            else Timeout / Error 4xx-5xx
                E-->>H: Error / Timeout
                H-->>S: PingResult(isAlive=false, latencyMs)
            end
            
            S->>M: processPingResult(service, pingResult)
            Note over M: Aplica Máquina de Estados<br/>(PENDING, HEALTHY, DEGRADED, DOWN, RECOVERING)
            
            alt Acumula 3 fallos consecutivos
                M->>DB: Guarda IncidentEntity (ONGOING)
            else Confirma 2 éxitos tras caída
                M->>DB: Cierra IncidentEntity (RESOLVED)
            end
            
            M->>DB: Actualiza status, latencia y lastCheckedAt
        end
    end
```
