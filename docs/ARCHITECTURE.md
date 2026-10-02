# Arquitectura del Sistema

Este documento describe cómo está estructurado **DeployOps** a nivel de código y componentes, detallando las responsabilidades de cada capa, el flujo de comunicación multiplataforma y la generación asíncrona de reportes.

---

## 1. C4 Model - Contexto del Sistema

El siguiente diagrama ilustra cómo los distintos actores e interfaces interactúan con el núcleo del sistema.

```mermaid
flowchart TD
    %% Definicion de colores al estilo C4 Model
    classDef person fill:#08427b,stroke:#052e56,color:#fff,rx:20,ry:20
    classDef system fill:#1168bd,stroke:#0b4884,color:#fff
    classDef external fill:#999999,stroke:#6b6b6b,color:#fff
    
    %% Nodos del Diagrama de Contexto
    Admin["👤 Administrador IT"]:::person
    
    DeployOps["⚙️ DeployOps Platform <br/> (Angular + Spring Boot)"]:::system
    
    APIs["🌐 APIs Externas <br/> (Ej: Google, GitHub, Stripe)"]:::external
    SMTP["📧 Servidor SMTP <br/> (Envío de Correos)"]:::external

    %% Flujo de interaccion
    Admin -->|Usa la plataforma para <br/> monitorear servicios| DeployOps
    DeployOps -->|Realiza Pings y <br/> Health Checks constantes a| APIs
    DeployOps -->|Envia Alertas y <br/> Reportes PDF a traves de| SMTP
```

---

## 2. Capas del Backend (Spring Boot)

El código Java está dividido estrictamente en capas para separar responsabilidades y facilitar las pruebas unitarias:

1.  **Controllers (Controladores):** Son la puerta de entrada HTTP. Exponen los endpoints REST, deserializan el JSON y delegan el trabajo. No contienen reglas de negocio.
2.  **Services (Servicios):** El corazón de la aplicación. Aquí vive la máquina de estados, el generador de reportes PDF (JasperReports), y las tareas programadas (Scheduler).
3.  **Repositories (Repositorios):** Interfaces de Spring Data JPA que se encargan exclusivamente de comunicarse con PostgreSQL mediante consultas JPQL y nativas.

---

## 3. Flujo Asíncrono de Monitoreo (WebSockets)

Para lograr una experiencia reactiva de "tiempo real" sin sobrecargar el servidor (evitando el polling constante), el sistema utiliza este flujo:

1.  El Frontend carga los datos iniciales vía REST.
2.  Inmediatamente abre una conexión persistente **WebSocket** y se suscribe al canal de eventos.
3.  Cuando el *Scheduler* del Backend detecta una falla en un servicio externo, cambia el estado en la base de datos y hace un "Push" de un mensaje JSON hacia todos los clientes conectados.
4.  El Frontend (Web o Móvil) procesa el evento y actualiza la pantalla al instante.

**Diagrama de Secuencia del Health Check:**

```mermaid
sequenceDiagram
    participant F as Cliente (Angular)
    participant B as Backend (Spring Boot)
    participant E as API Externa (Terceros)
    
    F->>B: GET /api/services (Carga inicial)
    B-->>F: JSON con estado actual
    F->>B: Conexión WebSocket (/topic/status)
    B-->>F: Conexión establecida
    
    loop Tarea programada (@Scheduled)
        B->>E: HTTP GET /health (Ping)
        
        alt Responde 200 OK
            E-->>B: 200 OK
            B->>B: Registra latencia en DB (UP)
            
        else Timeout o HTTP 5xx
            E-->>B: Error
            B->>B: Ejecuta lógica de reintentos
            B->>B: Persiste nuevo Incidente (DOWN)
            B-)F: Push WebSocket: {service_id: 1, status: "DOWN"}
            Note over F: El Frontend reacciona<br/>al instante y pinta rojo.
        end
    end
```
