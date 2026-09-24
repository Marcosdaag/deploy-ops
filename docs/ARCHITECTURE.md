# Arquitectura del Sistema

Detalle técnico de cómo está estructurado el backend, el frontend y la comunicación entre los componentes de DeployOps.

---

## 1. Visión general

El proyecto está diseñado como un sistema monolítico desacoplado mediante una **arquitectura en capas tradicional** en el backend y una **SPA en Angular** en el frontend. 

La idea de esta estructura es mantener una separación clara entre la interfaz, la lógica de negocio y el acceso a la base de datos, sin sumar la complejidad de red, despliegue y mantenimiento que implicaría dividir la aplicación en microservicios independientes.

```
+─────────────────────────────────────────────────────────────+
|                      Frontend Angular                       |
|           (Landing pública, Dashboard, Formularios)         |
+──────────────────────────────┬──────────────────────────────+
                               │ REST API / WebSockets
+──────────────────────────────v──────────────────────────────+
|                     Backend Spring Boot                     |
|                                                             |
|  [ Capa de Controladores ]   Endpoints REST, DTOs y         |
|                              validaciones de entrada        |
|              │                                              |
|  [ Capa de Servicios ]       Lógica de negocio, Scheduler,  |
|                              cliente HTTP y alertas         |
|              │                                              |
|  [ Capa de Repositorios ]    Persistencia y mapeo JPA       |
+──────────────────────────────┬──────────────────────────────+
                               │ Conexión JDBC (HikariCP)
                      +────────v─────────+
                      |    PostgreSQL    |
                      | (Supabase/Docker)|
                      +──────────────────+
```

---

## 2. Capas del Backend (Spring Boot)

### 2.1. Capa de Controladores (Presentación)
Es el punto de entrada de las peticiones que llegan desde el frontend:
* **Endpoints REST:** Expone los recursos bajo el prefijo `/api/v1/...`.
* **Uso de DTOs:** No se exponen las entidades de base de datos directamente al exterior. Se utilizan clases de transferencia (DTOs) para recibir datos y responder al cliente, protegiendo el modelo interno.
* **Validaciones:** Se validan los datos de entrada en el servidor (URLs correctas, campos obligatorios, intervalos válidos) antes de que lleguen a la lógica de negocio.
* **Manejo de errores:** Un controlador global de excepciones (`@RestControllerAdvice`) captura los errores y devuelve respuestas JSON con una estructura uniforme y códigos de estado HTTP adecuados (400, 404, 500).

### 2.2. Capa de Servicios (Lógica de Negocio)
Es el núcleo de la aplicación y contiene dos responsabilidades principales:
* **Operaciones de dominio:** Creación de servicios, asociación a entornos, cálculo de tiempos de respuesta y la lógica para determinar cuándo un servicio pasa a estar degradado, caído o recuperado.
* **Motor de monitoreo (Scheduler y llamadas HTTP):**
  * Un programador de tareas en segundo plano identifica periódicamente qué endpoints deben revisarse según el intervalo configurado.
  * Ejecuta las peticiones HTTP contra las URLs externas utilizando un cliente HTTP configurado con *timeouts* estrictos (5 segundos). Esto es fundamental para evitar que un servicio lento o inaccesible congele los hilos del servidor.
  * Para no sobrecargar el hilo principal de la aplicación, las comprobaciones se ejecutan mediante un pool de hilos en paralelo.

### 2.3. Capa de Repositorios (Persistencia)
Se encarga de la comunicación directa con PostgreSQL mediante Spring Data JPA:
* Define las operaciones CRUD y consultas personalizadas necesarias para el sistema.
* Utiliza consultas paginadas para el historial de comprobaciones, asegurando que consultar las métricas de un servicio con miles de registros no sobrecargue la memoria de la aplicación.
* Gestiona las transacciones de base de datos (`@Transactional`) para asegurar que los cambios de estado y la creación de incidentes se guarden de forma consistente.

---

## 3. Estructura del Frontend (Angular)

El frontend está pensado como un panel de control ágil y reactivo:

* **Componentes Standalone:** Se utiliza la arquitectura moderna de Angular sin módulos tradicionales (`NgModule`), lo que permite que el proyecto sea más liviano y modular.
* **Manejo de estado reactivo:** 
  * Se utilizan **Signals** para manejar el estado local de la interfaz de forma simple y reactiva (por ejemplo, el estado visual de una tarjeta o la carga de un formulario).
  * Se utiliza **RxJS** para gestionar los flujos asíncronos de datos, llamadas HTTP y conexiones en tiempo real.
* **Distribución de vistas:**
  * **Landing pública:** Muestra comprobaciones en vivo de servicios públicos de referencia (Google, GitHub Status) para que cualquier persona que entre al sitio pueda ver cómo funciona el panel sin necesidad de registrarse.
  * **Dashboard privado:** Panel de administración donde el usuario autenticado puede registrar sus propias APIs, ver el estado en tiempo real (`HEALTHY`, `DEGRADED`, `DOWN`), revisar gráficos de latencia y ver incidentes abiertos.

---

## 4. Comunicación entre Componentes

El sistema combina dos formas de comunicación para ser eficiente y no saturar el servidor:

1. **Peticiones HTTP (REST):** Se usan para las acciones habituales del usuario (iniciar sesión, registrar un nuevo servicio, cambiar configuraciones o pedir el historial de un día concreto).
2. **Eventos en tiempo real (WebSockets / SSE):** En lugar de hacer que el frontend pregunte cada 3 segundos si hay novedades (*polling*), el backend notifica al navegador únicamente cuando ocurre un evento importante (un servicio cayó, cambió de latencia o se recuperó).
3. **Peticiones salientes del sistema:** El backend actúa como cliente HTTP hacia internet para hacer los *pings* periódicos a las URLs monitoreadas.

---

## 5. Base de Datos y Conexión

* **PostgreSQL:** Actúa como la fuente única de datos (servicios, usuarios, configuraciones e historial de comprobaciones).
* **Entorno:** Se conecta a una base de datos PostgreSQL alojada en **Supabase** (o en un contenedor Docker en desarrollo local).
* **Pool de conexiones (HikariCP):** Spring Boot administra un conjunto de conexiones reutilizables hacia la base de datos para mantener baja la latencia y evitar abrir y cerrar conexiones en cada comprobación de salud.
