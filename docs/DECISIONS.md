# Technical Decisions and Trade-offs (ADR)

Este documento registra las decisiones arquitectónicas y técnicas más relevantes adoptadas durante el diseño de **DeployOps**, detallando el contexto, las alternativas evaluadas y las razones de cada elección.

---

## ADR 01: Arquitectura en Capas en un Monolito frente a Microservicios

* **Estado:** Aceptado.
* **Contexto:** Al diseñar una plataforma de confiabilidad, existe la tentación de dividir el sistema tempranamente en múltiples microservicios (servicio de auth, servicio de scheduler, servicio de incidentes, servicio de notificaciones).
* **Decisión:** Desarrollar un backend único con **arquitectura en capas tradicional** (Controller, Service, Repository).
* **Justificación:**
  * La complejidad del proyecto debe residir en las reglas de dominio (máquina de estados, detección de caídas, cálculo de latencias) y no en la infraestructura de red.
  * Los microservicios introducen problemas de latencia entre llamadas, necesidad de transacciones distribuidas, trazabilidad compleja y un costo de despliegue muy elevado para un solo desarrollador.
  * Una arquitectura en capas bien estructurada permite mantener límites limpios y cohesivos sin sobrecargar la operación del sistema.

---

## ADR 02: Notificaciones en Tiempo Real (Push) frente a Polling HTTP

* **Estado:** Aceptado.
* **Contexto:** El panel de control de Angular necesita reflejar de inmediato cuando una API cambia a estado `DOWN` o se recupera.
* **Decisión:** Utilizar un canal de eventos en tiempo real (**WebSockets o Server-Sent Events - SSE**) en lugar de sondeo periódico (*polling*) desde el cliente.
* **Justificación:**
  * El *polling* tradicional (hacer un `GET` cada 3 segundos desde cada cliente) satura la red y consume recursos de base de datos de forma innecesaria, donde el 99% de las peticiones devuelven exactamente la misma información.
  * La arquitectura orientada a eventos permite que el backend notifique al frontend **únicamente cuando ocurre una transición de estado real**, reduciendo la carga del servidor y mejorando la fluidez visual de la aplicación.

---

## ADR 03: PostgreSQL como Motor de Persistencia Relacional

* **Estado:** Aceptado.
* **Contexto:** Se requiere almacenar datos de configuración (servicios, credenciales de usuarios, entornos) junto con registros temporales de alta frecuencia (resultados de comprobaciones de salud).
* **Decisión:** Centralizar la persistencia en **PostgreSQL**, utilizando **Supabase** para el despliegue en la nube y Docker para desarrollo local.
* **Justificación:**
  * El modelo de datos es fuertemente relacional: un incidente pertenece a un servicio, una comprobación se asocia a un entorno específico y las reglas de integridad referencial (claves foráneas) evitan registros huérfanos.
  * PostgreSQL maneja con solvencia índices temporales sobre tablas de comprobaciones históricas mediante particionamiento o índices en campos `checked_at`.
  * Supabase provee una instancia gestionada de PostgreSQL estándar con alta disponibilidad, simplificando la conexión mediante JDBC estándar sin requerir configuraciones complejas de servidores dedicados.

---

## ADR 04: Demostración mediante Landing Pública con Servicios Reales frente a Simuladores Falsos

* **Estado:** Aceptado.
* **Contexto:** Es necesario que cualquier visitante o reclutador comprenda el funcionamiento de la plataforma en pocos segundos sin fricción de registro.
* **Decisión:** Diseñar una **landing page pública** que muestre comprobaciones en vivo de APIs públicas reales (Google, GitHub, Cloudflare), reservando el registro y login para la creación de monitores privados.
* **Justificación:**
  * Monitorear servicios públicos auténticos demuestra que el motor de comprobación HTTP, los cálculos de latencia y la renderización en vivo funcionan contra servidores reales de internet.
  * Elimina la necesidad de crear datos ficticios o botones artificiales en la interfaz, transmitiendo mayor seriedad técnica al mostrar un sistema operativo y en funcionamiento constante.

---

## ADR 05: Límites Deliberados de Alcance (Scope Boundaries)

Para evitar la sobreingeniería y garantizar una primera versión completamente funcional y testeable, se definen explícitamente los siguientes límites técnicos:

* **Sin agentes remotos en servidores de terceros:** Las comprobaciones se efectúan mediante sondas HTTP externas estándar. Instalar agentes en servidores cliente eleva drásticamente los requisitos de seguridad, certificados TLS y mantenimiento de software.
* **Sin orquestación con Kubernetes:** El sistema se empaqueta en imágenes Docker estándar para despliegue en servicios livianos (ej: Render, Railway o VPS), evitando la complejidad de gestión de clústeres.
* **Foco acotado (No clonar Datadog o Grafana):** El propósito no es ser un monitor de CPU/Memoria de infraestructura genérico, sino un hub enfocado en la relación entre **despliegues de software, salud de endpoints e incidentes operativos**.
