# Dominio y Reglas de Negocio

Este documento detalla la estructura logica de datos y las reglas exactas que dictan el comportamiento del motor de observabilidad en DeployOps.

---

## 1. Esquema de Base de Datos (ERD)

El modelo relacional persistido en PostgreSQL (Supabase en produccion / Docker local) estructura los servicios monitoreados, sus propietarios, la bitacora de incidentes y el registro de pings historicos:

```mermaid
erDiagram
    USERS ||--o{ SERVICES : owns
    SERVICES ||--o{ INCIDENTS : experiences
    SERVICES ||--o{ PING_LOGS : records

    USERS {
        bigint id PK
        string email UK
        string password_hash
        string name
        string public_slug UK
        timestamp created_at
    }

    SERVICES {
        bigint id PK
        bigint user_id FK "Nullable para endpoints de demostracion"
        string name "VARCHAR(100)"
        string url "VARCHAR(500)"
        string http_method "VARCHAR(10)"
        int expected_status_code
        string custom_headers "TEXT"
        string status "PENDING, HEALTHY, DEGRADED, DOWN, RECOVERING"
        int consecutive_failures
        int consecutive_successes
        bigint last_response_time_ms
        int ssl_days_remaining
        timestamp ssl_expiry_date
        boolean is_public
        boolean is_protected
        timestamp created_at
        timestamp last_checked_at
    }

    INCIDENTS {
        bigint id PK
        bigint service_id FK
        string status "ONGOING, RESOLVED"
        timestamp started_at
        timestamp resolved_at
    }

    PING_LOGS {
        bigint id PK
        bigint service_id FK
        int status_code
        bigint response_time_ms
        boolean is_success
        timestamp checked_at
    }
```

---

## 2. Maquina de Estados Finitos del Monitoreo

El sistema implementa una maquina de estados finitos disenada para evitar falsos positivos y parpadeos temporales de red:

```mermaid
stateDiagram-v2
    [*] --> PENDING : Alta del servicio (POST)

    PENDING --> HEALTHY : Primer sondeo exitoso
    PENDING --> DEGRADED : Primer sondeo fallido

    HEALTHY --> DEGRADED : 1er Fallo de red
    DEGRADED --> HEALTHY : 1er Exito (Falsa alarma transitoria)

    DEGRADED --> DOWN : 2 Fallos consecutivos adicionales (3 en total)
    note right of DOWN
      El sistema abre formalmente un 'Incidente'.
      (Estado: ONGOING, fecha startedAt)
    end note

    DOWN --> RECOVERING : 1er Exito tras la caida
    RECOVERING --> DOWN : 1 Fallo (Recaida inmediata)

    RECOVERING --> HEALTHY : 2do Exito consecutivo
    note right of HEALTHY
      El sistema sella y resuelve el 'Incidente'.
      (Estado: RESOLVED, fecha resolvedAt)
    end note
```

---

## 3. Reglas Criticas de Negocio

### 3.1. Estado Inicial Honesto
- **Regla:** Al crear un servicio (`POST /api/endpoints`), el sistema no asume disponibilidad perfecta antes de chequearlo.
- **Comportamiento:** Se registra con estado `PENDING` y `uptimePercentage = 0.0`. En la primera ejecucion del Scheduler pasa a `HEALTHY` si responde con el codigo esperado o a `DEGRADED` si presenta anomalias.

### 3.2. Tolerancia a Fallos y Declaracion de Caidas
- **Regla:** Un solo timeout o error de conexion no marca el servicio como `DOWN`.
- **Comportamiento:** El servicio pasa a estado de alerta temporal (`DEGRADED`). Solo si el fallo persiste durante tres ciclos consecutivos (3/3), el servicio transiciona oficialmente a `DOWN` y se persiste un registro `IncidentEntity` con estado `ONGOING`.

### 3.3. Resolucion de Incidentes por Estabilidad
- **Regla:** Un incidente en estado `ONGOING` unicamente transiciona a `RESOLVED` tras confirmar estabilidad comprobada.
- **Comportamiento:** Requiere dos respuestas satisfactorias consecutivas (`DOWN` -> `RECOVERING` -> `HEALTHY`). Al segundo exito, se estampa la marca temporal `resolvedAt` y se calcula la duracion total en segundos.

### 3.4. Escudo Defensivo contra SSRF
- **Regla:** La API no puede ser utilizada como vector para escanear redes internas, hosts locales o infraestructura cloud privada.
- **Comportamiento:** `UrlSecurityValidator` descarta esquemas no autorizados (restringiendo a `http` y `https`), bloquea nombres locales (`localhost`, `*.local`, `*.internal`, `*.arpa`), subredes privadas (RFC 1918), Carrier-Grade NAT (RFC 6598), endpoints de metadatos cloud (`169.254.169.254`) y puertos administrativos sensibles.

### 3.5. Aislamiento por Alcance (Dual-Scope)
- **Regla:** Los usuarios autenticados deben poder gestionar sus endpoints privados sin interferir con la infraestructura publica de demostracion.
- **Comportamiento:** El parametro `scope` filtra las consultas:
  - `scope=mine`: Retorna exclusivamente los endpoints asociados al usuario autenticado via su identificador JWT.
  - `scope=showcase`: Expone los endpoints de demostracion sembrados por la plataforma.
  - Los endpoints de demostracion poseen el flag `is_protected = true`, lo que impide su eliminacion accidental por parte de usuarios externos.

### 3.6. Calculo de Uptime SLA sin N+1 Queries
- **Regla:** La disponibilidad mensual debe calcularse sobre una ventana movil de 30 dias con precision de segundos y rendimiento constante.
- **Comportamiento:** Se utiliza el metodo `findOverlappingIncidentsForServices` de `IncidentRepository` que recupera todos los incidentes que intersectan la ventana de 30 dias en un unico viaje de red (`O(1)` consultas SQL). Los incidentes se agrupan en memoria y se calcula el tiempo de inactividad restando los solapamientos.

### 3.7. Purga Automatizada de Logs Historicos
- **Regla:** La tabla `ping_logs` no debe crecer de manera indefinida afectando el almacenamiento y la indexacion.
- **Comportamiento:** `MetricsHistoryService` ejecuta una tarea programada diaria que purga registros de ping con antiguedad superior a 30 dias, preservando unicamente los incidentes historicos consolidados.
