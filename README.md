# DeployOps

Plataforma B2B para centralizar el monitoreo de salud proactivo, el historial de latencias y la gestión de incidentes de servicios en tiempo real.

[![Java 21](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-20-dd0031?style=flat-square&logo=angular)](https://angular.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)](https://www.docker.com/)

---

## Sobre el proyecto

Cuando desarrollamos y mantenemos varias APIs o microservicios, la información operativa suele estar dispersa. **DeployOps** reúne todo en un panel central, permitiendo registrar servicios, ejecutar health checks programados de manera asíncrona, detectar caídas instantáneamente y alertar a los administradores.

### Características Proactivas Destacadas:

- **Monitoreo en tiempo real** con máquina de estados estricta (Healthy, Degraded, Down).
- **Actualización vía WebSockets** en el cliente Angular (sin refrescar la página).
- **Soporte Multiplataforma:** Cliente web (Angular) y futura app móvil nativa (Angular Native) para monitoreo _on-the-go_.
- **Alertas y Notificaciones:** Integración con JavaMailSender para avisos ante caídas críticas.
- **Reportes Semanales (Cron Jobs):** Generación de resúmenes de Uptime en PDF (usando JasperReports/iText) enviados automáticamente por correo.

---

## Documentación del Proyecto

Toda la especificación técnica, de diseño y arquitectónica ha sido modularizada para mantener este archivo introductorio limpio. Puedes consultar la documentación detallada en los siguientes enlaces:

- **[Arquitectura del Sistema (`docs/ARCHITECTURE.md`)](./docs/ARCHITECTURE.md):** Describe el diseño estructural en capas del backend (Spring Boot), el ecosistema cliente (Angular Web y Mobile) y el flujo de comunicación síncrona (HTTP) y asíncrona (WebSockets).
- **[Dominio y Reglas de Negocio (`docs/DOMAIN_AND_RULES.md`)](./docs/DOMAIN_AND_RULES.md):** Contiene la lógica central de la aplicación. Define el ciclo de vida de los servicios y la máquina de estados de los incidentes.
- **[Decisiones Técnicas (`docs/DECISIONS.md`)](./docs/DECISIONS.md):** Registro formal de las decisiones de diseño arquitectónico (ADR). Justifica la elección del stack tecnológico, tareas programadas y generación de PDFs.

---

## Stack Tecnológico

| Capa              | Tecnologías                                                                      |
| :---------------- | :------------------------------------------------------------------------------- |
| **Backend**       | Java 21, Spring Boot, Spring Data JPA, Spring Boot Actuator, Hibernate Validator |
| **Frontend**      | Angular, TypeScript, RxJS, Signals, TailwindCSS                                  |
| **Base de Datos** | PostgreSQL (Docker local o Supabase Cloud)                                       |
| **Testing**       | JUnit 5, Mockito, Vitest                                                         |

---

## Roadmap de Desarrollo

- [x] **Fase 1: Configuración inicial**
  - Estructura de monorepo (Spring Boot + Angular).
  - Definición de arquitectura, dominio y ADRs.
- [ ] **Fase 2: Core (Funcionalidad base)**
  - CRUD de servicios y entornos.
  - Tareas programadas (`@Scheduled`) para ejecución automática de health checks.
  - Persistencia de resultados e historial en PostgreSQL.
- [ ] **Fase 3: Tiempo Real y Proactividad**
  - Máquina de estados para abrir y cerrar incidentes automáticamente.
  - Actualización de la UI en Angular vía WebSockets.
  - Generación de reportes PDF y envío por Email.
- [ ] **Fase 4: Multiplataforma y Pulido**
  - Adaptación e inicialización del cliente Angular Native.
  - Despliegue en producción (Docker / PaaS).
