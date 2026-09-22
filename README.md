# DeployOps

Plataforma para centralizar el monitoreo de salud, historial de despliegues e incidentes de servicios en un solo lugar.

[![Java 21](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-20.3-dd0031?style=flat-square&logo=angular)](https://angular.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)](https://www.docker.com/)

---

## Sobre el proyecto

Cuando desarrollamos y mantenemos varias APIs o microservicios, la información operativa suele estar dispersa, el código en GitHub, los despliegues en el pipeline de CI/CD y los errores en los logs. **DeployOps** reúne todo eso en un panel central, permite registrar servicios y entornos, ejecutar health checks programados para medir tiempos de respuesta, detectar caídas y registrar incidentes vinculados a los cambios de versión en tiempo real.

---

## Arquitectura de la aplicación

El backend está estructurado siguiendo una **arquitectura en capas tradicional**, desacoplando la lógica de presentación, las reglas de negocio y el acceso a los datos para facilitar su mantenimiento y escalabilidad.

```
+-------------------------------------------------------------+
|                      Frontend Angular                       |
|           (Dashboard, métricas y estado en vivo)            |
+------------------------------+------------------------------+
                               | Peticiones HTTP / WebSockets
+------------------------------v------------------------------+
|                     Backend Spring Boot                     |
|                                                             |
|  [ Capa de Controladores ]   Endpoints REST y validaciones  |
|              |                                              |
|  [ Capa de Servicios ]       Lógica de negocio, Scheduler,  |
|              |               detección de incidentes        |
|              |                                              |
|  [ Capa de Repositorios ]    Consultas y mapeo JPA          |
+------------------------------+------------------------------+
                               |
                      +--------v---------+
                      |    PostgreSQL    |
                      | (Docker/Supabase)|
                      +------------------+
```

- **Capa de Controladores (Controller):** Expone los endpoints REST consumidos por Angular y valida los datos de entrada.
- **Capa de Servicios (Service):** Contiene la lógica de negocio, el scheduler para los health checks automáticos y la evaluación de estados de incidentes.
- **Capa de Datos (Repository):** Administra la persistencia e historial de verificaciones mediante Spring Data JPA.
- **Frontend (Angular):** Consume la API y muestra en tiempo real el estado, latencia e incidentes de cada servicio.

---

## Stack Tecnológico

| Capa              | Tecnologías                                                                            |
| :---------------- | :------------------------------------------------------------------------------------- |
| **Backend**       | Java 21, Spring Boot 4.1.1, Spring Data JPA, Spring Boot Actuator, Hibernate Validator |
| **Frontend**      | Angular 20.3, TypeScript, RxJS, Signals                                                |
| **Base de Datos** | PostgreSQL (Docker local o Supabase Cloud)                                             |
| **Testing**       | JUnit 5, Mockito                                                                       |

---

## Roadmap de Desarrollo

- [x] **Fase 1: Configuración inicial**
  - Estructura de monorepo (Spring Boot + Angular).
  - Definición de arquitectura, dominio y especificación de endpoints.
- [ ] **Fase 2: MVP (Funcionalidad base)**
  - CRUD de servicios y entornos.
  - Scheduler para ejecución automática de health checks.
  - Persistencia de resultados e historial en PostgreSQL.
  - Dashboard básico en Angular para ver el estado de las APIs.
- [ ] **Fase 3: Incidentes y Tiempo Real**
  - Lógica para abrir y cerrar incidentes según resultados de los checks.
  - Actualización de estado en Angular vía WebSockets/SSE sin refrescar la página.
- [ ] **Fase 4: Pulido y Pruebas**
  - Pruebas unitarias de servicios críticos.
  - Despliegue de la aplicación.

---

## Documentación del Proyecto

El detalle de diseño, reglas lógicas y contratos se encuentra en la carpeta [`docs/`](./docs):

- [**Arquitectura del Sistema (`docs/ARCHITECTURE.md`)**](./docs/ARCHITECTURE.md): Detalle de componentes y modelo de comunicación.
- [**Decisiones Técnicas (`docs/DECISIONS.md`)**](./docs/DECISIONS.md): Justificación de las decisiones técnicas y librerías utilizadas.
- [**Dominio y Reglas de Negocio (`docs/DOMAIN_AND_RULES.md`)**](./docs/DOMAIN_AND_RULES.md): Ciclo operativo, estados de servicio y reglas de incidentes.
- [**Especificación de la API (`docs/API_SPEC.md`)**](./docs/API_SPEC.md): Endpoints, payloads JSON y códigos de respuesta.
- [**Testing y Despliegue (`docs/TESTING_AND_DEVOPS.md`)**](./docs/TESTING_AND_DEVOPS.md): Estrategia de pruebas unitarias y entorno.
