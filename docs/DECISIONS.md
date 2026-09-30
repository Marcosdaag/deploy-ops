# Technical Decisions and Trade-offs (ADR)

Este documento registra las decisiones arquitectónicas y técnicas más relevantes adoptadas durante el diseño de **DeployOps**. Aquí detallamos el contexto, las alternativas evaluadas y las razones de cada elección para entender el "por qué" detrás del código.

---

## ADR 01: Arquitectura en Capas en un Monolito frente a Microservicios

* **Contexto:** Al diseñar una plataforma de monitoreo, existe la tentación de dividir el sistema rápidamente en múltiples microservicios (uno para auth, otro para el scheduler, otro para notificaciones).
* **Decisión:** Desarrollar un backend único con **arquitectura en capas tradicional** (Controller, Service, Repository).
* **Justificación:**
  * La verdadera complejidad de este proyecto reside en las reglas de negocio (la máquina de estados, detectar caídas, calcular latencias) y no en la infraestructura de red.
  * Los microservicios introducen problemas de latencia, necesidad de transacciones distribuidas y un costo de despliegue enorme que no justifica el alcance del MVP.
  * Una arquitectura en capas bien estructurada permite tener un código limpio y cohesivo sin la sobrecarga operativa de orquestar 5 contenedores distintos.

---

## ADR 02: Notificaciones en Tiempo Real (Push) frente a Polling HTTP

* **Contexto:** El panel de control (Dashboard) necesita reflejar inmediatamente cuando una API externa se cae o se recupera.
* **Decisión:** Utilizar un canal de eventos en tiempo real (**WebSockets**) en lugar de sondeos periódicos (*polling*) desde el cliente.
* **Justificación:**
  * El *polling* tradicional (hacer que Angular o React hagan un `GET` cada 3 segundos) satura la red y consume recursos de base de datos sin sentido, ya que el 99% de las veces la API responderá "sigue todo bien".
  * Los WebSockets permiten que el backend le notifique al frontend **únicamente cuando ocurre un cambio real**. Esto reduce drásticamente la carga del servidor y da una experiencia visual fluida.

---

## ADR 03: PostgreSQL como Motor de Persistencia Relacional

* **Contexto:** Necesitamos guardar datos estáticos (usuarios, servicios) y datos temporales de alta frecuencia (el historial de pings y latencias).
* **Decisión:** Centralizar todo en **PostgreSQL**, utilizando **Supabase** para producción y contenedores Docker para el desarrollo local.
* **Justificación:**
  * El modelo de datos es puramente relacional: un incidente le pertenece a un servicio, y un servicio tiene entornos. Usar SQL asegura que no queden datos "huérfanos" gracias a las claves foráneas.
  * PostgreSQL es lo suficientemente robusto para manejar tablas históricas grandes si en el futuro decidimos particionar la tabla de métricas.

---

## ADR 04: Demostración mediante Landing Pública (Con Servicios Reales)

* **Contexto:** Un reclutador o usuario que entra al proyecto necesita entender de qué trata en 5 segundos, sin la fricción de tener que crearse una cuenta.
* **Decisión:** Diseñar una **landing page pública** que muestre un dashboard en vivo monitoreando APIs públicas reales (Google, GitHub, etc.), reservando el login solo para crear monitores privados.
* **Justificación:**
  * Monitorear servicios reales demuestra que el motor HTTP de Spring Boot funciona de verdad contra internet.
  * Le da muchísima seriedad técnica al portafolio al no usar datos ficticios o "mockeados".

---

## ADR 05: Límites Deliberados de Alcance (El MVP)

Para evitar la sobreingeniería y terminar el proyecto en tiempos lógicos, pusimos estos límites estrictos:
* **Sin agentes instalables:** No le vamos a pedir a los usuarios que instalen un agente en sus servidores. Haremos pings HTTP desde nuestro backend hacia afuera.
* **Sin Kubernetes:** El sistema se empaqueta en Docker estándar para subirlo a plataformas PaaS (Render, Railway), sin complicarnos la vida con orquestación avanzada.

---

## ADR 06: Implementación de Frontend Dual (Angular y React)

* **Contexto:** El backend necesita un cliente web para mostrar el dashboard. 
* **Decisión:** Construir **dos** aplicaciones cliente independientes, una en **Angular** y otra en **React**, que consuman exactamente la misma API de Spring Boot.
* **Justificación:**
  * Funciona como un laboratorio técnico. Nos permite medir en carne propia cómo cada framework maneja el re-renderizado constante de datos por WebSockets.
  * Demuestra una capacidad de adaptación brutal en el perfil profesional, comprobando que la lógica pesada vive en el backend y el frontend es solo una capa de presentación intercambiable.
