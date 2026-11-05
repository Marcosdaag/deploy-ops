# DeployOps

Plataforma de observabilidad y monitoreo proactivo de disponibilidad HTTP, auditoría de certificados SSL y gestión de incidentes en tiempo real.

Enlace a la aplicación en producción: [https://deployopsapp.vercel.app/](https://deployopsapp.vercel.app/)

[![Java 21](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-20.3-red?style=flat-square&logo=angular)](https://angular.dev/)
[![Three.js](https://img.shields.io/badge/Three.js-r187-black?style=flat-square&logo=threedotjs)](https://threejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)](https://www.docker.com/)
[![CI](https://img.shields.io/badge/CI-GitHub_Actions-2088FF?style=flat-square&logo=githubactions)](./.github/workflows/backend-ci.yml)
[![Tests](https://img.shields.io/badge/Tests-149_passing-success?style=flat-square)](https://github.com/Marcosdaag/deploy-ops)

---

## Galería Visual de la Plataforma

### 1. Panel Principal con Globo 3D de Telemetría Global
Visualización inmersiva en WebGL con Three.js que representa la red de nodos del clúster geolocalizados, arcos de datos intercontinentales y métricas de salud en tiempo real sobre una textura procedural de 4096x2048 px con contornos vectoriales continentales de Natural Earth.

![Panel Principal con Globo 3D de Telemetría Global](./docs/screenshots/01_hero_3d_telemetry_globe.png)

### 2. Libro Mayor de Observabilidad SRE (Observability Ledger)
Arquitectura desacoplada y de alta densidad que erradica el anidamiento artificial de tarjetas (sin contenedores redundantes ni bordes duplicados). Presenta latencias p95 monoespaciadas, ritmos de disponibilidad SLA a 30 días, monitoreo de certificados TLS y estado sincronizado del clúster en un único pie técnico integrado.

![Libro Mayor SRE](./docs/screenshots/02_sre_observability_ledger.png)

### 3. Pipeline de Datos y Motor de Concurrencia
Explorador de arquitectura interactivo que desglosa el ciclo de vida de cada comprobación: desde el despachador de Virtual Threads de Java 21 (Project Loom), la validación de seguridad perimetral anti-SSRF, la máquina de estados finitos hasta la persistencia O(1) y la emisión reactiva Server-Sent Events (SSE).

![Pipeline de Datos y Motor Loom](./docs/screenshots/03_interactive_pipeline_loom.png)

### 4. Consola REST Pública y Terminal cURL Interactiva
Consola técnica desacoplada para desarrolladores con navegación por pestañas de métodos HTTP (`GET`, `POST`), inspección de cabeceras de seguridad, previsualización de respuestas JSON y comandos cURL listos para copiar y ejecutar en terminal.

![Consola API REST y Terminal](./docs/screenshots/04_api_curl_interactive_terminal.png)

### 5. Soporte Integral de Modo Claro (Light Mode)
Sistema de diseño conmutable de descanso visual inspirado en estándares de GitHub Primer, Stripe y Linear, asegurando contraste estricto WCAG 2.1 AA e iluminación atmosférica balanceada.

![Soporte de Modo Claro](./docs/screenshots/05_light_mode_support.png)

### 6. Adaptabilidad Total en Dispositivos Móviles (375px)
Diseño responsive de precisión verificado en pantallas móviles: colapso inteligente de navegación, adaptación de métricas, protección de ancho mínimo en tablas de telemetría y cero desbordamiento horizontal (`hasOverflow: false`).

![Adaptabilidad Móvil](./docs/screenshots/06_mobile_responsive_ledger.png)

---

## Descripción General

DeployOps es una solución diseñada para centralizar la telemetría, el control de disponibilidad y el ciclo de vida de incidentes en infraestructuras web y microservicios. A diferencia de las herramientas tradicionales basadas en sondeos pasivos, DeployOps implementa un motor de ejecución asíncrona concurrente mediante Virtual Threads (Java 21), una máquina de estados finitos que erradica falsas alarmas y un perímetro estricto de validación contra vulnerabilidades Server-Side Request Forgery (SSRF).

La plataforma opera en dos niveles: un panel operativo autenticado con aislamiento de recursos privados y un motor de Status Page pública accesible mediante identificadores desacoplados (slugs) para mantener informados a los usuarios finales.

---

## Capacidades Técnicas del Sistema

### 1. Monitoreo Concurrente con Hilos Virtuales
- Planificación periódica fija (`@Scheduled(fixedDelay = 60000)`) orquestada mediante `Executors.newVirtualThreadPerTaskExecutor()`.
- Pings HTTP sin bloqueo de sockets del sistema operativo, con timeouts estrictos (3 segundos de conexión, 5 segundos de lectura).
- Medición de latencia monotónica de alta precisión en milisegundos a través de `System.nanoTime()`, eliminando discrepancias por desfases de reloj de sistema (NTP).
- Inspección directa de certificados TLS/SSL mediante sockets `X509Certificate` para alertar anticipadamente sobre vencimientos de certificados.

### 2. Máquina de Estados Finitos y Mitigación de Falsos Positivos
El motor de evaluación evita alertar ante parpadeos transitorios de red mediante una política estricta de tres confirmaciones:
- `PENDING`: Estado inicial honesto asignado en la creación. Ningún servicio asume 100% de disponibilidad antes de su primer sondeo.
- `HEALTHY`: El servicio responde con el código de estado esperado.
- `DEGRADED`: Primer o segundo fallo consecutivo de red. Se registra la anomalía sin disparar una declaración formal de incidente.
- `DOWN`: Confirmación de tres fallos consecutivos (3/3). Se abre formalmente un registro de incidente (`ONGOING`) con marca temporal inmutable.
- `RECOVERING`: Primera respuesta satisfactoria posterior a una caída. Si reincide en el siguiente ciclo, regresa a `DOWN`.
- Resolución: Requiere dos respuestas exitosas consecutivas en `RECOVERING` para transicionar a `HEALTHY` y sellar el incidente como `RESOLVED`.

```mermaid
stateDiagram-v2
    [*] --> PENDING : Creacion de Endpoint
    PENDING --> HEALTHY : HTTP 2xx/3xx
    HEALTHY --> DEGRADED : Fallo 1/3 (Sin alarma)
    DEGRADED --> DEGRADED : Fallo 2/3 (Sin alarma)
    DEGRADED --> DOWN : Fallo 3/3 (Declara Incidente ONGOING)
    DOWN --> RECOVERING : Exito 1/2
    RECOVERING --> DOWN : Reincidencia inmediata
    RECOVERING --> HEALTHY : Exito 2/2 (Cierra Incidente RESOLVED)
    HEALTHY --> HEALTHY : Sondeo exitoso recurrente
```

### 3. Escudo de Seguridad contra Server-Side Request Forgery (SSRF)
Dado que el sistema recibe URLs arbitrarias provistas por los usuarios, `UrlSecurityValidator` ejecuta una inspección defensiva previa a cada conexión:
- Restricción estricta de esquemas: únicamente se autorizan `http` y `https`.
- Deshabilitación de redirecciones automáticas en la capa HTTP para prevenir ataques de redirección abierta hacia interfaces internas.
- Resolución de nombres DNS vía `InetAddress` y descarte inmediato de direcciones de loopback (`127.0.0.0/8`), enlaces locales (`169.254.0.0/16`), Carrier-Grade NAT (`100.64.0.0/10`) y subredes privadas RFC 1918 (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`).
- Protección de endpoints de metadatos de proveedores Cloud (`169.254.169.254`, `fd00:ec2::254`).
- Bloqueo de puertos sensibles comúnmente asociados a bases de datos, administración de sistemas y contenedores (SSH, Docker, Redis, PostgreSQL, MySQL, Kubernetes API, Elasticsearch).

```mermaid
flowchart LR
    InputURL["URL provista por usuario"] --> SchemeCheck{"Esquema HTTP/HTTPS?"}
    SchemeCheck -- No --> RejectProto["Rechazo: Protocolo no autorizado"]
    SchemeCheck -- Si --> DNSResolve["Resolucion DNS (InetAddress)"]
    DNSResolve --> IPFilter{"Es IP Publica enrutable?"}
    IPFilter -- Loopback / Privada / Link-Local / Metadatos Cloud --> RejectIP["Rechazo: Direccion IP bloqueada"]
    IPFilter -- Si --> PortCheck{"Puerto seguro?"}
    PortCheck -- Puerto critico bloqueado (DB/SSH/Docker) --> RejectPort["Rechazo: Puerto bloqueado"]
    PortCheck -- Si --> VirtualThread["Ejecucion en Virtual Thread (Java 21)"]
```

### 4. Cálculo de Disponibilidad SLA sin Problema N+1
- Algoritmo de cálculo de disponibilidad sobre ventana móvil de 30 días con precisión de segundos.
- Consulta JPQL en lote (`findOverlappingIncidentsForServices`) que recupera todos los incidentes que cruzan la ventana temporal en un único viaje de red, agrupando los datos en memoria en tiempo constante.

### 5. Telemetría en Vivo y Canal Unidireccional
- Emisión reactiva de eventos vía Server-Sent Events (SSE) a través de `/api/endpoints/stream`.
- El cliente web mantiene la vista sincronizada sin incurrir en peticiones periódicas agresivas (*polling*), reduciendo la sobrecarga sobre el balanceador y la base de datos.

### 6. Seguridad y Control de Tasa (Rate Limiting)
- Autenticación sin estado basada en JSON Web Tokens (JWT) firmados criptográficamente y contraseñas cifradas con `BCrypt`.
- Filtro de control de tasa (`RateLimitingFilter`) que implementa el algoritmo Token Bucket por dirección IP, protegiendo los endpoints de autenticación y creación contra abusos.
- Cabeceras HTTP de seguridad y política CORS configurada por lista blanca de orígenes.

### 7. Observabilidad Visual 3D y Telemetría Geoespacial (Three.js WebGL)
- Esfera interactiva de telemetría global desarrollada con Three.js (r187) y Angular 20.3.
- Mapeo esférico geodésico exacto con coordenadas reales de clústeres: US-East (`38.0, -78.0`), Buenos Aires (`-34.6, -58.4`), Santiago (`-33.4, -70.7`), París/Frankfurt (`48.8, 2.3`), Tokio (`35.7, 139.7`) y Sídney (`-33.9, 151.2`).
- Textura cartográfica de 4096 x 2048 px con contornos vectoriales continentales de Natural Earth, trazo dual de resplandor y anisotropía x4.
- Modelo físico de rotación con inercia y fricción cinemática (arrastre libre y continuación suave de giro sin reseteo de orientación).
- Libro Mayor SRE (*Observability Ledger*) unificado y desacoplado, sin anidamiento artificial de tarjetas ni contenedores de caja redundantes.

---

## Arquitectura de Componentes

```mermaid
flowchart TD
    subgraph Cliente ["Capa de Presentacion (Angular 20.3 + Three.js r187)"]
        Dashboard["Dashboard Component<br/>(Signals + Control Flow)"]
        Globe3D["TelemetryCanvas3dComponent<br/>(Three.js WebGL Engine)"]
        StatusPage["Status Page Component<br/>(Public Slug View)"]
        SSEClient["SSE Reactive Client"]
    end

    subgraph Perimetro ["Perimetro de Seguridad y Red"]
        RLF["RateLimitingFilter<br/>(Token Bucket por IP)"]
        JWTF["JwtAuthenticationFilter<br/>(Validacion de Tokens)"]
        SSRF["UrlSecurityValidator<br/>(Anti-SSRF + Bloqueo de Puertos)"]
    end

    subgraph Core ["Capa de Negocio (Spring Boot 4.1.1)"]
        EMS["EndpointManagerService<br/>(Maquina de Estados + CRUD)"]
        MS["MetricsService<br/>(Calculo Uptime SLA 30d sin N+1)"]
        HS["MetricsHistoryService<br/>(Series Temporales 24h/7d/30d)"]
        RS["ReportService<br/>(Generacion de Reportes CSV)"]
        SPS["StatusPageService<br/>(Agregacion Publica)"]
        SSEN["SseNotificationService<br/>(Transmision de Eventos)"]
    end

    subgraph Background ["Motor Asincrono Concurrente"]
        SCH["MonitoringScheduler<br/>(@Scheduled fixedDelay=60s)"]
        VT["Virtual Thread Executor<br/>(Java 21 Loom)"]
        HM["HttpMonitorService<br/>(HTTP Check + SSL Inspector)"]
    end

    subgraph Persistencia ["Capa de Datos (PostgreSQL 16)"]
        SR["ServiceRepository"]
        IR["IncidentRepository"]
        PLR["PingLogRepository"]
        UR["UserRepository"]
    end

    Dashboard --> Globe3D
    Dashboard --> RLF
    StatusPage --> RLF
    RLF --> JWTF
    JWTF --> EMS
    JWTF --> SPS
    EMS --> SSRF
    SCH --> VT
    VT --> HM
    HM --> EMS
    EMS --> SSEN
    SSEN -.->|Server-Sent Events| SSEClient
    EMS --> SR
    EMS --> IR
    EMS --> PLR
    EMS --> MS
    SPS --> SR
    SPS --> IR
    EMS --> UR
```

---

## Cobertura de Pruebas y Calidad de Código

El repositorio cuenta con una suite integral de **149 pruebas automatizadas** ejecutadas en entornos de integración continua:

| Módulo | Pruebas | Framework / Herramientas | Enfoque |
| :--- | :--- | :--- | :--- |
| **Backend Core** | 116 | JUnit 5, Mockito, MockMvc, H2 Database | Validación anti-SSRF (44 tests), transiciones de máquina de estados, cálculo de Uptime sin N+1, purga de logs, auditoría SSL y autenticación JWT |
| **Frontend SPA** | 33 | Jasmine, Karma, ChromeHeadless | Reactividad de Signals, filtrado de estados, ordenamiento de telemetría, canvas WebGL 3D, drawers de inspección, servicios HTTP e interceptores |
| **Total** | **149** | **100% pruebas pasando exitosamente** | **0 fallos, 0 errores en CI** |

---

## Especificación de la API REST

### Autenticación y Cuentas
| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Público | Registro de nuevo usuario con contraseña cifrada en BCrypt. |
| `POST` | `/api/auth/login` | Público | Autenticación de credenciales y emisión de token JWT. |
| `GET` | `/api/auth/me` | Autenticado | Retorna el perfil y slug público del usuario actual. |

### Gestión de Endpoints y Monitoreo
| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/endpoints` | Autenticado / Público | Lista de servicios según alcance (`scope=mine` o `scope=showcase`). |
| `POST` | `/api/endpoints` | Autenticado | Registro de un nuevo endpoint con validación anti-SSRF. |
| `PUT` | `/api/endpoints/{id}` | Autenticado | Actualización de parámetros, URL y umbrales de respuesta. |
| `DELETE` | `/api/endpoints/{id}` | Autenticado | Eliminación segura en cascada del servicio y sus incidentes asociados. |
| `POST` | `/api/endpoints/{id}/ping` | Autenticado | Disparo inmediato de comprobación HTTP bajo demanda. |
| `GET` | `/api/endpoints/{id}/metrics` | Autenticado | Historial de latencias y estado según rango (`24h`, `7d`, `30d`). |
| `GET` | `/api/endpoints/{id}/incidents` | Autenticado | Bitácora de incidentes activos y resueltos con duración en segundos. |
| `GET` | `/api/endpoints/{id}/report/csv` | Autenticado | Exportación descargable de reporte mensual de disponibilidad SLA en formato CSV. |
| `GET` | `/api/endpoints/stream` | Público | Flujo unidireccional de Server-Sent Events con métricas en tiempo real. |

### Status Page Pública
| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/status/{slug}` | Público | Estado consolidado de los servicios públicos marcados por el usuario. |

---

## Modelo de Datos

```mermaid
erDiagram
    USERS ||--o{ SERVICES : owns
    SERVICES ||--o{ INCIDENTS : registers
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
        bigint user_id FK "Nullable para servicios demo"
        string name
        string url
        string http_method
        int expected_status_code
        string custom_headers
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

## Variables de Entorno

### Backend (Spring Boot)
| Variable | Descripción | Valor por Defecto |
| :--- | :--- | :--- |
| `PORT` | Puerto de escucha del servidor HTTP | `8080` |
| `DB_URL` | URL JDBC de conexión a PostgreSQL | `jdbc:postgresql://localhost:5432/deployops_db` |
| `DB_USER` | Usuario de base de datos | `admin` |
| `DB_PASSWORD` | Contraseña de base de datos | `adminpassword` |
| `JWT_SECRET` | Clave secreta para firma y verificación de tokens | Cadena interna de desarrollo |
| `JWT_EXPIRATION_MS` | Tiempo de vida del token en milisegundos | `86400000` (24 horas) |
| `CORS_ALLOWED_ORIGINS` | Orígenes web autorizados separados por coma | `https://deployopsapp.vercel.app,http://localhost:4200` |
| `MAIL_ENABLED` | Habilitación del despachador de alertas por correo | `false` |
| `MAIL_HOST` | Servidor SMTP para envío de alertas | `smtp.gmail.com` |
| `MAIL_PORT` | Puerto SMTP | `587` |
| `MAIL_USERNAME` | Cuenta de correo emisora | Cadena vacía |
| `MAIL_PASSWORD` | Contraseña de aplicación SMTP | Cadena vacía |
| `MAIL_FROM` | Remitente visible en los correos | `alerts@deployops.io` |

### Frontend (Angular)
| Variable / Archivo | Descripción |
| :--- | :--- |
| `src/environments/environment.ts` | Configuración para desarrollo local apuntando a `http://localhost:8080` |
| `src/environments/environment.prod.ts` | Configuración de producción apuntando a la API en Render |

---

## Ejecución en Entorno Local

### Requisitos Previos
- Java Development Kit (JDK) 21 LTS o superior
- Node.js versión 20.x o 22.x LTS
- Docker y Docker Compose

### 1. Iniciar la Base de Datos Local
```bash
docker compose up -d
```

### 2. Ejecutar y Probar el Backend
```bash
cd backend

# Ejecutar las 116 pruebas unitarias y de integracion
./mvnw test

# Iniciar la aplicacion
./mvnw spring-boot:run
```
La API estará accesible en `http://localhost:8080`.

### 3. Ejecutar y Probar el Frontend
```bash
cd frontend

# Instalar dependencias
npm install

# Ejecutar las 33 pruebas unitarias
npm test -- --watch=false --browsers=ChromeHeadless

# Iniciar servidor de desarrollo
npm start
```
El panel web estará disponible en `http://localhost:4200`.

---

## Licencia y Mantenimiento

Proyecto desarrollado y mantenido por Marcos Da Agostini como solución integral de observabilidad web y gestión proactiva de servicios. Código fuente bajo licencia MIT.
