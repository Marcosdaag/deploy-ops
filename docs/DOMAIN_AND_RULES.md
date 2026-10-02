# Dominio y Reglas de Negocio

Este documento detalla la estructura lógica de los datos y las reglas exactas que dictan cómo se comporta la aplicación frente a los distintos escenarios. Define el núcleo funcional de **DeployOps**.

---

## 1. Esquema de Base de Datos (ERD)

La información se estructura de manera relacional para mantener un historial preciso de incidentes, pings y configuraciones, aislado por usuario.

```mermaid
erDiagram
    USER ||--o{ SERVICE : owns
    SERVICE ||--o{ ENVIRONMENT : has
    SERVICE ||--o{ INCIDENT : experiences
    ENVIRONMENT ||--o| HEALTH_CHECK_CONFIG : configured_by
    ENVIRONMENT ||--o{ CHECK_RESULT : generates

    USER {
        UUID id PK
        string email
        string password
    }
    
    SERVICE {
        UUID id PK
        UUID user_id FK
        string name "Ej: API Pagos"
        string repository_url
    }
    
    ENVIRONMENT {
        UUID id PK
        UUID service_id FK
        string name "Ej: Production, Staging"
        string base_url
    }
    
    HEALTH_CHECK_CONFIG {
        UUID id PK
        UUID environment_id FK
        string endpoint "Ej: /api/health"
        int interval_seconds
        int expected_status "Ej: 200"
    }
    
    INCIDENT {
        UUID id PK
        UUID service_id FK
        string status "OPEN o RESOLVED"
        datetime started_at
        datetime resolved_at
    }
```

---

## 2. Máquina de Estados del Monitoreo

El sistema no genera incidentes al primer fallo de red para evitar los "falsos positivos" o parpadeos temporales. Sigue un flujo riguroso de confirmación, integrado con el sistema de Alertas Push y Email.

```mermaid
stateDiagram-v2
    [*] --> HEALTHY
    
    HEALTHY --> DEGRADED : 1 Fallo de red
    DEGRADED --> HEALTHY : 1 Éxito (Era falsa alarma)
    
    DEGRADED --> DOWN : 2 Fallos más consecutivos (3 en total)
    note right of DOWN
      El sistema abre un 'Incidente'.
      Dispara Push Notification al
      móvil y avisa por WebSockets.
    end note
    
    DOWN --> RECOVERING : 1er Éxito tras la caída
    RECOVERING --> DOWN : 1 Fallo (Recaída)
    
    RECOVERING --> HEALTHY : 2do Éxito consecutivo
    note right of HEALTHY
      El sistema cierra el 'Incidente'.
      (Estado: RESOLVED)
    end note
```

---

## 3. Reglas Críticas del Sistema

### 3.1. Confirmación de Caídas (Tolerancia a fallos)
*   **Regla:** Un solo *timeout* o error HTTP 500 no marca el servicio como `DOWN`.
*   **Comportamiento:** Pasa a un estado de alerta temporal (`DEGRADED`). Solo si el fallo persiste durante 3 ciclos de comprobación consecutivos, el servicio se considera caído oficialmente. En ese instante se abre un incidente y se disparan las alarmas a los clientes Web y Móvil.

### 3.2. Cierre de Incidentes
*   **Regla:** Un incidente en estado `OPEN` solo puede pasar a `RESOLVED` si el sistema comprueba y certifica la estabilidad del servicio.
*   **Comportamiento:** Requiere 2 respuestas `HTTP 200 OK` consecutivas para confirmar que el servicio levantó y no fue solo un reinicio intermitente.

### 3.3. Reportes Periódicos
*   **Regla:** El cliente debe recibir analíticas sin intervención manual.
*   **Comportamiento:** Un proceso `Cron` lee el historial de estados de la semana, calcula el porcentaje de *Uptime* (ej. 99.9%), inyecta estos datos en una plantilla PDF generada al vuelo y se la envía al usuario por email de forma automática.

### 3.4. Aislamiento por Usuario (Multi-tenancy básico)
*   **Regla:** Un usuario no puede ver ni modificar los servicios de otro.
*   **Comportamiento:** Todas las consultas SQL que extraen datos deben ir filtradas por el `user_id` del token JWT actual del usuario que hizo la petición. La única excepción son los servicios marcados explícitamente como "globales" para la *landing page* pública.
