# Decisiones Tecnicas y de Arquitectura (ADR)

Este documento formaliza las decisiones tecnicas y arquitectonicas adoptadas durante el desarrollo de DeployOps. Cada registro describe el contexto, las alternativas evaluadas y las razones de diseno que fundamentan el codigo fuente.

---

## ADR 01: Arquitectura Monolitica en Capas frente a Microservicios

- **Contexto:** Al planificar una plataforma de monitoreo de servicios, es comun considerar la separacion prematura en multiples microservicios (servicio de autenticacion, servicio de ejecucion de pings, servicio de notificaciones).
- **Decision:** Desarrollar un backend monolitico estructurado en capas estrictas (Controller, Service, Repository, Security).
- **Justificacion:**
  - La complejidad del sistema reside en las reglas de transicion de estados, la precision de medicion y el aislamiento de recursos, no en la escala de red.
  - Los microservicios introducen sobrecarga operacional (latencia de red entre servicios, consistencia eventual, orquestacion compleja) que no aporta valor al caso de uso central.
  - Una arquitectura monolitica bien delimitada maximiza la cohesion interna y permite ejecutar pruebas automatizadas en milisegundos sin depender de harnesses distribuidos.

---

## ADR 02: Telemetria en Tiempo Real via Server-Sent Events (SSE) frente a Polling HTTP

- **Contexto:** El panel de control debe reflejar cambios de estado de salud y latencia tan pronto como el motor concluye un ciclo de sondeo.
- **Decision:** Implementar Server-Sent Events (SSE) unidireccional a traves de `/api/endpoints/stream` en lugar de sondeos periodicos constantes (*polling*) desde el cliente o WebSockets bidireccionales pesados.
- **Justificacion:**
  - El flujo de telemetria es por definicion unidireccional (del servidor hacia los navegadores).
  - WebSockets requiere mantener canales bidireccionales con protocolos de handshake dedicados y manejo complejo de reconexion a nivel de capa de transporte.
  - SSE opera de manera nativa sobre HTTP estándar, atraviesa proxies y balanceadores sin friccion y reconecta de forma automatica mediante la API del navegador (`EventSource`).

---

## ADR 03: PostgreSQL como Motor de Persistencia Relacional

- **Contexto:** Se requiere almacenar entidades maestras (usuarios, configuracion de endpoints) y registros de eventos temporales (bitacora de incidentes y pings).
- **Decision:** Utilizar PostgreSQL 16 a traves de Supabase (usando el Transaction Pooler IPv4) en produccion y contenedores Docker para desarrollo local.
- **Justificacion:**
  - Las relaciones entre entidades exigen integridad referencial estricta (un incidente pertenece inmutablemente a un endpoint, y un endpoint a un usuario).
  - PostgreSQL provee soporte robusto para consultas de rango temporal (clausulas de superposicion con `tsrange` y operadores de fecha).
  - Supabase garantiza persistencia gestionada en la nube y alta disponibilidad sin limites temporales de expiracion.

---

## ADR 04: Concurrencia de Monitoreo mediante Hilos Virtuales (Java 21 Virtual Threads)

- **Contexto:** Chequear decenas de endpoints simultaneamente mediante hilos de plataforma tradicionales (`Thread`) satura rapidamente el pool de hilos del sistema operativo debido a la latencia de espera de red (I/O bloqueante).
- **Decision:** Orquestar la ejecucion concurrente de pings mediante `Executors.newVirtualThreadPerTaskExecutor()`.
- **Justificacion:**
  - Los hilos virtuales de Project Loom permiten crear miles de hilos ligeros por segundo sin sobrecargar la memoria ni la CPU.
  - Evita la necesidad de recurrir a frameworks reactivos complejos (WebFlux/Reactor), preservando un modelo de programacion imperativo, facil de depurar y mantener con stack traces completos.

---

## ADR 05: Medicion de Latencia Monotonica (`System.nanoTime`)

- **Contexto:** Al medir el tiempo de respuesta de una peticion HTTP, utilizar `System.currentTimeMillis()` expone las metricas a desfases causados por ajustes de reloj del sistema operativo (sincronizacion NTP o cambios de zona horaria), lo que puede producir mediciones anomalas o latencias negativas.
- **Decision:** Utilizar `System.nanoTime()` exclusivamente para calcular la duracion de cada ping (`startNano` vs `endNano`) y convertir el diferencial a milisegundos con `TimeUnit.NANOSECONDS.toMillis()`.
- **Justificacion:**
  - La fuente de tiempo monotonica garantiza que el reloj siempre avance linealmente sin importar ajustes del reloj de pared del sistema operativo.

---

## ADR 06: Escudo Defensivo contra Server-Side Request Forgery (SSRF)

- **Contexto:** Al permitir a los usuarios registrar cualquier URL para su monitoreo, atacantes podrian ingresar direcciones privadas internas (`localhost`, `127.0.0.1`, `169.254.169.254`) para escanear servicios internos o extraer credenciales del proveedor de nube.
- **Decision:** Construir un componente de validacion (`UrlSecurityValidator`) ejecutado previo a guardar la URL y previo a cada ejecucion de ping.
- **Justificacion:**
  - Deshabilita el seguimiento automatico de redirecciones HTTP (`connection.setInstanceFollowRedirects(false)`) para anular vectores de bypass via redirect.
  - Resuelve las direcciones IP asociadas y bloquea loopback, direcciones de enlace local, subredes privadas RFC 1918 y puertos administrativos sensibles (bases de datos, SSH, Docker daemon).
  - Respaldado por una suite de 44 pruebas unitarias que validan exhaustivamente combinaciones de direcciones IPv4, IPv6 y nombres de host ofuscados.

---

## ADR 07: Optimizacion de Consultas en Lote (Solucion al Problema N+1)

- **Contexto:** Al consultar el listado de endpoints en el dashboard (`GET /api/endpoints`), calcular el Uptime de cada servicio individualmente generaba una consulta adicional a la tabla de incidentes por cada fila ($N+1$ queries).
- **Decision:** Implementar el metodo `findOverlappingIncidentsForServices` en `IncidentRepository`, el cual extrae todos los incidentes relevantes de la ventana en una unica consulta SQL.
- **Justificacion:**
  - Reduce las transferencias de red a la base de datos de $N+1$ a exactamente una consulta fija.
  - La agrupacion por identificador de servicio se resuelve en memoria con Java Streams en tiempo lineal $O(N)$.

---

## ADR 08: Control de Tasa por IP mediante Token Bucket (Rate Limiting)

- **Contexto:** Los endpoints de autenticacion (`/api/auth/login`, `/api/auth/register`) y creacion de endpoints son objetivos frecuentes de ataques de fuerza bruta y denegacion de servicio.
- **Decision:** Incorporar un filtro de servlet (`RateLimitingFilter`) con implementacion en memoria del algoritmo Token Bucket por direccion IP.
- **Justificacion:**
  - Protege el servidor contra ataques de saturacion sin requerir la instalacion de un cluster externo de Redis.
  - Aplica politicas diferenciadas: limites mas estrictos para autenticacion (10 peticiones/minuto) y margenes razonables para consultas regulares.

---

## ADR 09: Arquitectura de Frontend en Angular 20 con Signals y Paleta SRE

- **Contexto:** El panel de observabilidad requiere alta legibilidad de datos tecnicos, ausencia de fatiga visual y transiciones de estado instantaneas.
- **Decision:** Utilizar Angular 20 Standalone con reactividad basada en Signals, diseno con tokens CSS de alto contraste inspirados en herramientas de ingenieria (Datadog, Better Stack) y ausencia total de gradientes de neon o artefactos decorativos innecesarios.
- **Justificacion:**
  - Signals permite una deteccion de cambios granular sin incurrir en ciclos globales de `ngDoCheck`.
  - El sistema de diseno asegura contraste WCAG AAA y ergonomia en sesiones operativas prolongadas.
