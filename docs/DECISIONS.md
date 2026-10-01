# Technical Decisions and Trade-offs (ADR)

Este documento registra las decisiones arquitectÃ³nicas y tÃ©cnicas mÃ¡s relevantes adoptadas durante el diseÃ±o de **DeployOps**. AquÃ­ detallamos el contexto, las alternativas evaluadas y las razones de cada elecciÃ³n para entender el "por quÃ©" detrÃ¡s del cÃ³digo.

---

## ADR 01: Arquitectura en Capas en un Monolito frente a Microservicios

* **Contexto:** Al diseÃ±ar una plataforma de monitoreo, existe la tentaciÃ³n de dividir el sistema rÃ¡pidamente en mÃºltiples microservicios (uno para auth, otro para el scheduler, otro para notificaciones).
* **DecisiÃ³n:** Desarrollar un backend Ãºnico con **arquitectura en capas tradicional** (Controller, Service, Repository).
* **JustificaciÃ³n:**
  * La verdadera complejidad de este proyecto reside en las reglas de negocio (la mÃ¡quina de estados, detectar caÃ­das, calcular latencias) y no en la infraestructura de red.
  * Los microservicios introducen problemas de latencia, necesidad de transacciones distribuidas y un costo de despliegue enorme que no justifica el alcance del MVP.
  * Una arquitectura en capas bien estructurada permite tener un cÃ³digo limpio y cohesivo sin la sobrecarga operativa de orquestar 5 contenedores distintos.

---

## ADR 02: Notificaciones en Tiempo Real (Push) frente a Polling HTTP

* **Contexto:** El panel de control (Dashboard) necesita reflejar inmediatamente cuando una API externa se cae o se recupera.
* **DecisiÃ³n:** Utilizar un canal de eventos en tiempo real (**WebSockets**) en lugar de sondeos periÃ³dicos (*polling*) desde el cliente.
* **JustificaciÃ³n:**
  * El *polling* tradicional (hacer que Angular o React hagan un `GET` cada 3 segundos) satura la red y consume recursos de base de datos sin sentido, ya que el 99% de las veces la API responderÃ¡ "sigue todo bien".
  * Los WebSockets permiten que el backend le notifique al frontend **Ãºnicamente cuando ocurre un cambio real**. Esto reduce drÃ¡sticamente la carga del servidor y da una experiencia visual fluida.

---

## ADR 03: PostgreSQL como Motor de Persistencia Relacional

* **Contexto:** Necesitamos guardar datos estÃ¡ticos (usuarios, servicios) y datos temporales de alta frecuencia (el historial de pings y latencias).
* **DecisiÃ³n:** Centralizar todo en **PostgreSQL**, utilizando **Supabase** para producciÃ³n y contenedores Docker para el desarrollo local.
* **JustificaciÃ³n:**
  * El modelo de datos es puramente relacional: un incidente le pertenece a un servicio, y un servicio tiene entornos. Usar SQL asegura que no queden datos "huÃ©rfanos" gracias a las claves forÃ¡neas.
  * PostgreSQL es lo suficientemente robusto para manejar tablas histÃ³ricas grandes si en el futuro decidimos particionar la tabla de mÃ©tricas.

---

## ADR 04: DemostraciÃ³n mediante Landing PÃºblica (Con Servicios Reales)

* **Contexto:** Un reclutador o usuario que entra al proyecto necesita entender de quÃ© trata en 5 segundos, sin la fricciÃ³n de tener que crearse una cuenta.
* **DecisiÃ³n:** DiseÃ±ar una **landing page pÃºblica** que muestre un dashboard en vivo monitoreando APIs pÃºblicas reales (Google, GitHub, etc.), reservando el login solo para crear monitores privados.
* **JustificaciÃ³n:**
  * Monitorear servicios reales demuestra que el motor HTTP de Spring Boot funciona de verdad contra internet.
  * Le da muchÃ­sima seriedad tÃ©cnica al portafolio al no usar datos ficticios o "mockeados".

---

## ADR 05: LÃ­mites Deliberados de Alcance (El MVP)

Para evitar la sobreingenierÃ­a y terminar el proyecto en tiempos lÃ³gicos, pusimos estos lÃ­mites estrictos:
* **Sin agentes instalables:** No le vamos a pedir a los usuarios que instalen un agente en sus servidores. Haremos pings HTTP desde nuestro backend hacia afuera.
* **Sin Kubernetes:** El sistema se empaqueta en Docker estÃ¡ndar para subirlo a plataformas PaaS (Render, Railway), sin complicarnos la vida con orquestaciÃ³n avanzada.

---

## ADR 06: ImplementaciÃ³n de Frontend Dual (Angular y React)

* **Contexto:** El backend necesita un cliente web para mostrar el dashboard. 
* **DecisiÃ³n:** Construir **dos** aplicaciones cliente independientes, una en **Angular** y otra en **React**, que consuman exactamente la misma API de Spring Boot.
* **JustificaciÃ³n:**
  * Funciona como un laboratorio tÃ©cnico. Nos permite medir en carne propia cÃ³mo cada framework maneja el re-renderizado constante de datos por WebSockets.
  * Demuestra una capacidad de adaptaciÃ³n brutal en el perfil profesional, comprobando que la lÃ³gica pesada vive en el backend y el frontend es solo una capa de presentaciÃ³n intercambiable.

