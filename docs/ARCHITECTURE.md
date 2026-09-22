# Architecture Overview

Este documento describe la arquitectura técnica de **DeployOps**, detallando la organización en capas del backend, la estructura del frontend en Angular y los patrones de comunicación del sistema.

---

## 1. Diseño General: Arquitectura en Capas

El backend en Spring Boot sigue una **arquitectura en capas desacoplada**, donde cada nivel tiene una responsabilidad única y bien delimitada:

```
+─────────────────────────────────────────────────────────────+
|                      Frontend Angular                       |
|           (Landing pública, Dashboard, Formularios)         |
+──────────────────────────────┬──────────────────────────────+
                               │ REST API (JSON) / WebSockets
+──────────────────────────────v──────────────────────────────+
|                     Backend Spring Boot                     |
|                                                             |
|  [ Presentation Layer ]      Controllers REST, DTOs,        |
|                              Validaciones (@Valid)          |
|              │                                              |
|  [ Service / Domain Layer ]  Lógica de negocio, Scheduler,  |
|                              Cliente HTTP, Máquina Estados  |
|              │                                              |
|  [ Data Access Layer ]       Spring Data JPA Repositories   |
+──────────────────────────────┬──────────────────────────────+
                               │ JDBC (Conexión pooled)
                      +────────v─────────+
                      |    PostgreSQL    |
                      | (Supabase/Docker)|
                      +──────────────────+
```

---

## 2. Responsabilidades por Capa (Backend)

### 2.1. Capa de Presentación (Presentation Layer)
* **Controllers REST:** Exponen los endpoints HTTP bajo la convención `/api/v1/...`.
* **Data Transfer Objects (DTOs):** Clases dedicadas para recibir datos (*Request DTOs*) y devolver respuestas (*Response DTOs*), evitando exponer las entidades JPA directamente.
* **Validación de Entrada:** Uso de anotaciones de Bean Validation (`@NotNull`, `@NotBlank`, `@Size`, `@URL`) para rechazar peticiones inválidas con código `HTTP 400 Bad Request` antes de alcanzar la lógica de negocio.
* **Manejo Global de Errores:** Clase anotada con `@RestControllerAdvice` que captura excepciones del sistema y las transforma en respuestas JSON estructuradas y consistentes.

### 2.2. Capa de Servicios y Dominio (Service Layer)
* **Servicios de Negocio:** Implementan las reglas definidas en `DOMAIN_AND_RULES.md` (apertura y cierre de incidentes, cálculo de métricas de disponibilidad).
* **Motor de Ejecución (Scheduler & HTTP Probe):**
  * Tarea programada en segundo plano encargada de identificar los endpoints que requieren verificación según su intervalo configurado.
  * Cliente HTTP moderno de Spring (`RestClient` o `HttpClient`) configurado con *timeouts* estrictos de conexión y lectura (ej: 5 segundos) para evitar el agotamiento de hilos del servidor ante servicios que no responden.
* **Pool de Concurrencia:** Ejecución asíncrona de las sondas HTTP mediante un `ThreadPoolTaskExecutor` dedicado, permitiendo verificar múltiples endpoints en paralelo sin afectar el rendimiento de la API REST.

### 2.3. Capa de Acceso a Datos (Data Access Layer)
* **Spring Data JPA Repositories:** Interfaces que extienden `JpaRepository` para operaciones CRUD y consultas derivadas.
* **Consultas Optimizadas:** Uso de proyecciones y paginación para consultas de históricos de comprobaciones, evitando cargar grandes volúmenes de datos en memoria.
* **Transaccionalidad:** Uso explícito de `@Transactional` en métodos de servicio que modifican el estado de servicios e incidentes para garantizar la consistencia en base de datos.

---

## 3. Arquitectura del Frontend (Angular)

El frontend está desarrollado con Angular bajo un paradigma moderno y modular:

* **Componentes Standalone:** Estructura modular sin necesidad de `NgModule` tradicionales, facilitando la carga diferida (*lazy loading*) por rutas.
* **Gestión de Estado Reactivo:** Uso combinado de **Signals** para el estado local y reactividad en plantillas, y **RxJS** para la gestión de flujos de datos asíncronos y peticiones HTTP.
* **Organización de Vistas:**
  * **Landing Pública:** Muro de solo lectura que consume los checks de servicios públicos de referencia.
  * **Área Privada (Dashboard):** Tarjetas de servicios del usuario, indicadores visuales de salud (`HEALTHY`, `DEGRADED`, `DOWN`), latencias y tiempos de respuesta.
  * **Detalle del Servicio:** Visualización del histórico de comprobaciones mediante gráficos temporales y tabla de incidentes.
* **Servicios HTTP:** Clientes centralizados para interactuar con la API del backend, con interceptores para el manejo uniforme de errores y cabeceras de autenticación.

---

## 4. Patrones de Comunicación

| Tipo de Comunicación | Protocolo / Tecnología | Uso en el Sistema |
| :--- | :--- | :--- |
| **Síncrona (Petición/Respuesta)** | HTTP / REST (JSON) | Operaciones de CRUD (crear servicios, editar entornos, consultar históricos). |
| **Tiempo Real (Servidor a Cliente)** | WebSockets / SSE | Notificaciones push al dashboard de Angular cuando un check cambia el estado de un servicio o se abre/cierra un incidente. |
| **Sondeo Saliente** | HTTP Client (Java) | Ejecución periódica de las solicitudes de comprobación de salud contra los endpoints de las APIs monitoreadas. |

---

## 5. Estrategia de Persistencia (PostgreSQL)

* **Proveedor:** PostgreSQL estándar, compatible tanto en desarrollo local (contenedor Docker) como en producción (instancia gestionada en la nube mediante **Supabase**).
* **Pool de Conexiones:** Gestión eficiente mediante HikariCP (incluido por defecto en Spring Boot) para reutilizar conexiones y minimizar la latencia de acceso a datos.
