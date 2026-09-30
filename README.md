# DeployOps

Plataforma para centralizar el monitoreo de salud, historial de despliegues e incidentes de servicios en un solo lugar.

[![Java 21](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.0-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-18-dd0031?style=flat-square&logo=angular)](https://angular.dev/)
[![React](https://img.shields.io/badge/React-18-61DAFB?style=flat-square&logo=react)](https://react.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)](https://www.docker.com/)

---

## Sobre el proyecto

Cuando desarrollamos y mantenemos varias APIs o microservicios, la información operativa suele estar dispersa: el código en GitHub, los despliegues en el pipeline de CI/CD y los errores en los logs. **DeployOps** reúne todo eso en un panel central, permite registrar servicios y entornos, ejecutar health checks programados para medir tiempos de respuesta, detectar caídas y registrar incidentes vinculados a los cambios de versión en tiempo real.

---

## Documentación del Proyecto

Para mantener este archivo introductorio limpio y enfocado, toda la especificación técnica, de diseño y arquitectónica fue modularizada. Puedes consultar la documentación detallada en los siguientes enlaces, según la información que requieras:

*   **[Arquitectura del Sistema (`docs/ARCHITECTURE.md`)](./docs/ARCHITECTURE.md):** Describe el diseño estructural en capas del backend (Spring Boot), la implementación dual del frontend (Angular/React) y el flujo de comunicación síncrona (HTTP) y asíncrona (WebSockets).
*   **[Dominio y Reglas de Negocio (`docs/DOMAIN_AND_RULES.md`)](./docs/DOMAIN_AND_RULES.md):** Contiene la lógica central de la aplicación. Define el ciclo de vida de los servicios, la máquina de estados de los incidentes (transiciones entre Healthy, Degraded, Down, etc.) y las políticas de aislamiento de datos.
*   **[Decisiones Técnicas (`docs/DECISIONS.md`)](./docs/DECISIONS.md):** Registro formal de las decisiones de diseño arquitectónico (ADR). Justifica la elección del stack tecnológico, las librerías implementadas y los patrones de diseño aplicados.

---

## Stack Tecnológico

| Capa              | Tecnologías                                                                            |
| :---------------- | :------------------------------------------------------------------------------------- |
| **Backend**       | Java 21, Spring Boot, Spring Data JPA, Spring Boot Actuator, Hibernate Validator       |
| **Frontend**      | Angular, React, TypeScript, RxJS, Signals, Vite                                        |
| **Base de Datos** | PostgreSQL (Docker local o Supabase Cloud)                                             |
| **Testing**       | JUnit 5, Mockito, Vitest, Jest                                                         |

---

## Roadmap de Desarrollo

- [x] **Fase 1: Configuración inicial**
  - Estructura de monorepo (Spring Boot + Angular + React).
  - Definición de arquitectura, dominio y especificación de endpoints.
- [ ] **Fase 2: MVP (Funcionalidad base)**
  - CRUD de servicios y entornos.
  - Scheduler para ejecución automática de health checks.
  - Persistencia de resultados e historial en PostgreSQL.
  - Dashboard básico para ver el estado de las APIs.
- [ ] **Fase 3: Incidentes y Tiempo Real**
  - Lógica para abrir y cerrar incidentes según resultados de los checks.
  - Actualización de estado en Angular/React vía WebSockets sin refrescar la página.
- [ ] **Fase 4: Pulido y Pruebas**
  - Pruebas unitarias de servicios críticos y componentes.
  - Despliegue de la aplicación.
