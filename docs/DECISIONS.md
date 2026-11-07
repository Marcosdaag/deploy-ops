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
- **Evolucion en v2.0:** Extendido por **ADR 10** para compartir ventanas de cuota en Redis 7 cuando existen multiples instancias del backend, preservando la cubeta en memoria como respaldo *Fail-Open*.

---

## ADR 09: Arquitectura de Frontend en Angular 20 con Signals y Paleta SRE

- **Contexto:** El panel de observabilidad requiere alta legibilidad de datos tecnicos, ausencia de fatiga visual y transiciones de estado instantaneas.
- **Decision:** Utilizar Angular 20 Standalone con reactividad basada en Signals, diseno con tokens CSS de alto contraste inspirados en herramientas de ingenieria (Datadog, Better Stack) y ausencia total de gradientes de neon o artefactos decorativos innecesarios.
- **Justificacion:**
  - Signals permite una deteccion de cambios granular sin incurrir en ciclos globales de `ngDoCheck`.
  - El sistema de diseno asegura contraste WCAG AAA y ergonomia en sesiones operativas prolongadas.

---

## ADR 10: Coordinacion Multi-Nodo con ShedLock y Rate Limiting Distribuido en Redis 7 (Fail-Open)

- **Contexto:** Al escalar horizontalmente el backend a dos o mas contenedores detras de un balanceador de carga para garantizar alta disponibilidad, surgen dos anomalias: (1) todas las instancias ejecutan simultaneamente `@Scheduled` (`MonitoringScheduler` y `MetricsHistoryService.purgeOldLogs`), triplicando los sondeos hacia las APIs monitoreadas y generando condiciones de carrera sobre `consecutive_failures`; y (2) las cubetas de `RateLimiterService` en memoria local permiten a un atacante multiplicar su cuota rotando peticiones entre nodos. Adicionalmente, en despliegues cloud gratuitos (ej. Render sin add-on externo de Redis) la aplicacion no debe colapsar al iniciar ni ante microcortes del cluster de cache.
- **Decision:** Incorporar `Redis 7.2` junto con `ShedLock` (`RedisLockProvider` bajo namespace `deployops`) envuelto en un decorador `ResilientLockProvider`, y migrar `RateLimiterService` a incrementos atomicos distribuidos (`INCR` + `PEXPIRE`) parametrizados mediante `app.redis.enabled`, conservando un respaldo automatico en memoria local (*Fail-Open*).
- **Justificacion:**
  - Garantiza que un unico nodo lider ejecute cada barrido de 60 segundos (`lockAtMostFor = 50s`, `lockAtLeastFor = 15s`); si el contenedor lider cae, otra replica asume el liderazgo en el ciclo siguiente sin intervencion humana.
  - Unifica el conteo de peticiones por IP en todo el cluster en tiempo $O(1)$.
  - El patron *Fail-Open* evita que Redis se convierta en un punto unico de fallo (*Single Point of Failure*): ante una caida transitoria de Redis o cuando `REDIS_ENABLED=false`, los schedulers y el control de tasa degradan limpiamente a memoria local sin interrumpir el servicio HTTP ni hacer fallar `/actuator/health`.

---

## ADR 11: Cache Distribuida L2 en Status Page con Invalidacion Reactiva por Eventos de Estado

- **Contexto:** Cuando un servicio monitoreado sufre una caida real, miles de usuarios finales consultan simultaneamente la pagina de estado publica (`GET /api/status/{slug}`), generando un efecto de estampida (*Thundering Herd*) sobre PostgreSQL. Sin embargo, aplicar un cache tradicional con TTL largo mostraria un estado `OPERATIONAL` falso durante varios minutos tras declararse un incidente.
- **Decision:** Implementar `StatusPageCacheService` sobre Redis (`deployops:status_page:{slug}`, serializado con Jackson 3 `JsonMapper` y `TTL` corto de seguridad de `60s`), combinado con **invalidacion reactiva inmediata** disparada desde `EndpointManagerService` y `StatusPageService`.
- **Justificacion:**
  - Absorbe picos masivos de lectura sirviendo el `StatusPageDTO` pre-calculado desde memoria RAM en `~1-2 ms` con cero consultas SQL en los *Cache Hits*.
  - En el instante exacto en que `processPingResult` detecta un cambio de `ServiceStatus` (ej. `HEALTHY -> DEGRADED` o `DEGRADED -> DOWN`), o cuando el propietario modifica la visibilidad o su `publicSlug`, se elimina la clave correspondiente en Redis, garantizando consistencia inmediata en momentos criticos e instrumentando contadores `hits`, `misses` y `evictions` en Micrometer.


---

## ADR 12: Patron Transactional Outbox en PostgreSQL frente a Publicacion Directa (Dual-Write) y Rescate de Zombies

- **Contexto:** Al confirmarse un incidente critico (`DOWN`) o su recuperacion (`HEALTHY`), el sistema debe alertar a los canales externos configurados por los clientes (Slack, Discord, webhooks personalizados). Despachar estas notificaciones HTTP directamente dentro del hilo de monitoreo bloquea el ciclo de sondeo, mientras que publicar de forma inmediata hacia un broker externo como RabbitMQ fuera de la transaccion SQL expone al sistema al clasico problema de *Dual-Write*: si PostgreSQL confirma el incidente pero el broker falla, o viceversa, se pierden alertas criticas o se generan inconsistencias irrecuperables.
- **Decision:** Implementar el patron *Transactional Outbox* persistiendo un registro inmutable en la tabla `outbox_events` con estado `PENDING` dentro de la **misma transaccion atomica `@Transactional`** de `EndpointManagerService` que actualiza el estado del servicio y crea el incidente. Un relay desacoplado (`OutboxRelayService`) sondea lotes pendientes con exclusión mutua ShedLock (`outboxRelaySweepLock`), cambia su estado a `PROCESSING` marcando `locked_at`, publica hacia el Topic Exchange `deployops.incidents` de RabbitMQ (o delega a Virtual Threads si `app.rabbitmq.enabled=false`), y marca los eventos como `PROCESSED`. Incluye un reaper automatico que rescata eventos `PROCESSING` estancados por mas de 2 minutos ante caidas abruptas de nodos y una purga programada a los 7 dias.
- **Justificacion:**
  - Garantiza consistencia transaccional ACID: un incidente jamas existe en la base de datos sin su correspondiente evento Outbox registrado.
  - El broker de mensajeria puede experimentar microcortes o mantenimientos sin que se pierda ninguna notificacion.
  - El mecanismo de *Zombie Lock Reaper* protege el cluster contra particiones de red o reinicios de contenedores en pleno despacho.

---

## ADR 13: Cifrado en Reposo AES-256-GCM, Firma HMAC-SHA256 Anti-Replay, Idempotencia y Anti-DNS-Rebinding Pre-Vuelo en Webhooks

- **Contexto:** La apertura de webhooks hacia endpoints arbitrarios definidos por los usuarios introduce serios vectores de vulnerabilidad: (1) exposicion de secretos compartidos ante filtraciones de la base de datos; (2) suplantacion o ataques de repeticion (*Replay Attacks*) de eventos capturados por atacantes en la red; (3) duplicacion de alertas operativas por semantica de entrega *At-Least-Once*; (4) ataques de *DNS Rebinding* y SSRF donde un dominio publico muta su resolucion DNS hacia direcciones privadas de la infraestructura (ej. `127.0.0.1` o metadatos de cloud `169.254.169.254`) despues del registro inicial; y (5) ataques de denegacion de servicio si el servidor receptor devuelve flujos infinitos de datos.
- **Decision:** Establecer una defensa en profundidad en cinco capas:
  1. **Cifrado AES-256-GCM:** Los secretos `whsec_...` se cifran de forma transparente con `SecretCryptoConverter` usando un IV aleatorio de 12 bytes y autenticacion GCM de 128 bits. El secreto plano solo se expone una vez al crear o rotar; en lecturas subsiguientes se retorna enmascarado (`whsec_••••••••4f9a`).
  2. **Firma HMAC-SHA256 Anti-Replay:** La cabecera `X-DeployOps-Signature-256` sella la concatenacion `timestampEpochSeconds + "." + rawJsonBody`. Los receptores verifican que el timestamp no se desvie mas de 300 segundos y comparan el digesto en tiempo constante (`MessageDigest.isEqual`).
  3. **Idempotencia Determinista:** Cada entrega transporta `X-DeployOps-Delivery-Id` y la tabla `webhook_delivery_failures` impone restriccion unica sobre `(event_id, webhook_id)`.
  4. **Anti-DNS-Rebinding Pre-Vuelo y Tope de 4 KB:** `WebhookDeliveryService` ejecuta `UrlSecurityValidator.validatePublicUrl()` inmediatamente antes de cada conexion HTTP, prohibe redirecciones 3xx y limita la lectura de la respuesta remota a un maximo estricto de 4096 bytes.
  5. **Resiliencia con Backoff + Jitter y DLQ:** Aplica 3 intentos con retroceso exponencial (2s, 8s, 30s) mas jitter aleatorio (+/- 20%). Tras el tercer fallo, el mensaje se deriva a la Dead Letter Queue (`webhooks.dlq`) y se audita para inspeccion y reintento manual.
- **Justificacion:**
  - Cumple estandares criptograficos empresariales (RFC 2104, NIST SP 800-38D).
  - Impide que DeployOps sea utilizado como pivote para escanear redes internas o saturar servidores de terceros.
