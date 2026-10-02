# Technical Decisions and Trade-offs (ADR)

Este documento registra las decisiones arquitectónicas y técnicas más relevantes adoptadas durante el diseño de **DeployOps**. Aquí detallamos el contexto, las alternativas evaluadas y las razones de cada elección para entender el "por qué" detrás del código.

---

## ADR 01: Arquitectura en Capas en un Monolito frente a Microservicios

* **Contexto:** Al diseñar una plataforma de monitoreo, existe la tentación de dividir el sistema rápidamente en múltiples microservicios (uno para autenticación, otro para el scheduler, otro para notificaciones).
* **Decisión:** Desarrollar un backend único con **arquitectura en capas tradicional** (Controller, Service, Repository).
* **Justificación:**
  * La verdadera complejidad de este proyecto reside en las reglas de negocio (la máquina de estados, detectar caídas, calcular latencias) y no en la infraestructura de red.
  * Los microservicios introducen problemas de latencia, necesidad de transacciones distribuidas y un costo de despliegue enorme que no justifica el alcance inicial del proyecto.
  * Una arquitectura en capas bien estructurada permite tener un código limpio y cohesivo sin la sobrecarga operativa de orquestar múltiples contenedores.

---

## ADR 02: Notificaciones en Tiempo Real (Push) frente a Polling HTTP

* **Contexto:** El panel de control (Dashboard) necesita reflejar inmediatamente cuando una API externa se cae o se recupera.
* **Decisión:** Utilizar un canal de eventos en tiempo real (**WebSockets**) en lugar de sondeos periódicos (*polling*) desde el cliente.
* **Justificación:**
  * El *polling* tradicional (hacer que Angular haga un `GET` cada 3 segundos) satura la red y consume recursos de base de datos sin sentido, ya que el 99% de las veces la API responderá "sigue todo bien".
  * Los WebSockets permiten que el backend le notifique al frontend **únicamente cuando ocurre un cambio real**. Esto reduce drásticamente la carga del servidor y brinda una experiencia visual absolutamente fluida.

---

## ADR 03: PostgreSQL como Motor de Persistencia Relacional

* **Contexto:** Necesitamos guardar datos estáticos (usuarios, servicios) y datos temporales de alta frecuencia (el historial de pings y latencias).
* **Decisión:** Centralizar todo en **PostgreSQL**, utilizando **Supabase** para producción y contenedores Docker para el desarrollo local.
* **Justificación:**
  * El modelo de datos es puramente relacional: un incidente le pertenece a un servicio, y un servicio tiene entornos. Usar SQL asegura que no queden datos "huérfanos" gracias a las claves foráneas (Foreign Keys).
  * PostgreSQL es lo suficientemente robusto para manejar tablas históricas grandes en caso de decidir particionar la tabla de métricas en el futuro.

---

## ADR 04: Demostración mediante Landing Pública (Con Servicios Reales)

* **Contexto:** Un reclutador o usuario que entra al proyecto necesita entender de qué trata en 5 segundos, sin la fricción de tener que crearse una cuenta.
* **Decisión:** Diseñar una **landing page pública** que muestre un dashboard en vivo monitoreando APIs públicas reales (Google, GitHub, etc.), reservando el login cerrado solo para crear monitores privados.
* **Justificación:**
  * Monitorear servicios reales demuestra que el motor HTTP de Spring Boot funciona y se conecta con el exterior verdaderamente.
  * Otorga seriedad técnica al portafolio al no usar datos ficticios o "mockeados".

---

## ADR 05: Límites Deliberados de Alcance (El MVP)

Para evitar la sobreingeniería y mantener el foco en la calidad del código, establecimos los siguientes límites estrictos:
* **Sin agentes instalables:** No pediremos a los usuarios que instalen un agente físico en sus servidores. Haremos pings HTTP desde nuestro backend hacia afuera, u ofreceremos Webhooks simples.
* **Sin Kubernetes:** El sistema se empaquetará en Docker Compose estándar para subirlo a plataformas PaaS, sin complicar la vida con orquestación avanzada en esta etapa.

---

## ADR 06: Reportes PDF y Tareas Programadas (Scheduled Tasks)

* **Contexto:** Los usuarios necesitan un resumen del estado de sus servicios sin tener que entrar a la aplicación todos los días.
* **Decisión:** Implementar un motor de tareas programadas (`@Scheduled` o Quartz) en Spring Boot que, de forma periódica, recopile las métricas de la base de datos, genere un reporte en PDF y lo envíe por correo electrónico (JavaMailSender).
* **Justificación:**
  * Transforma el sistema de un "dashboard pasivo" a una herramienta verdaderamente "proactiva".
  * Demuestra capacidades técnicas corporativas en el manejo de archivos generados al vuelo (PDFs) y la ejecución asíncrona programada (Cron).

---

## ADR 07: Estrategia Multiplataforma (Angular Web + Angular Native)

* **Contexto:** El monitoreo de infraestructura es una tarea crítica que exige atención 24/7, a menudo requiriendo notificaciones directamente al bolsillo del administrador.
* **Decisión:** Construir el ecosistema cliente utilizando **Angular** para la plataforma Web, y proyectar una aplicación móvil nativa utilizando **Angular Native** (`ng-native`).
* **Justificación:**
  * Evita la experiencia "emulada" y pesada de los WebViews (como Ionic), garantizando un rendimiento puro y acceso directo a las Notificaciones Push del teléfono (FCM/APNs).
  * Permite reutilizar la lógica, la inyección de dependencias y el conocimiento del framework (Angular) en ambas plataformas, demostrando un diseño de frontend altamente escalable.
