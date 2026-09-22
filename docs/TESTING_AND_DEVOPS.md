# Testing and DevOps

Este documento define la estrategia de pruebas unitarias, el manejo de configuraciones sensibles y el entorno de despliegue para **DeployOps**.

---

## 1. Estrategia de Testing (JUnit 5 & Mockito)

El objetivo principal de las pruebas automatizadas es garantizar que la lógica de negocio crítica funcione de manera determinista y sin fallos imprevistos.

### 1.1. Pruebas de la Máquina de Estados de Incidentes
Se prueban de forma exhaustiva las reglas de transición definidas en `DOMAIN_AND_RULES.md`:

* **Transición a DEGRADED:** Verificar que un único fallo no abra un incidente, sino que sitúe el servicio en advertencia.
* **Apertura de Incidente:** Simular 3 fallos consecutivos y verificar que:
  * El estado del servicio pase a `DOWN`.
  * Se instancie y persista una entidad `Incident` con estado `OPEN` y marca de tiempo correcta.
* **Estabilidad y Recuperación:**
  * Simular 1 respuesta exitosa (`HTTP 200`) tras estar en `DOWN` y verificar que el incidente **continúe abierto** (estado transitorio `RECOVERING`).
  * Simular una segunda respuesta exitosa consecutiva y verificar que el incidente cambie a `RESOLVED` y se calcule adecuadamente la duración del corte.

### 1.2. Pruebas del Servicio de Sondeo (Health Check Probe)
Utilizando **Mockito** para aislar llamadas de red externas:

* **Simulación de respuestas HTTP 200:** Comprobar el registro correcto del tiempo de respuesta (ms) y estado `UP`.
* **Simulación de Timeouts / Excepciones de Red:** Simular que el endpoint tarda más de los segundos límite configurados y verificar que el sistema capture la excepción sin interrumpir el scheduler, registrando el fallo con código de error apropiado.

### 1.3. Pruebas de Validación en Controladores
Verificar la capa de presentación mediante `@WebMvcTest`:

* Comprobar que peticiones con URLs mal formadas o campos obligatorios vacíos sean rechazadas con código `HTTP 400 Bad Request`.
* Comprobar que la estructura JSON de error devuelta coincida exactamente con la convención de `API_SPEC.md`.

---

## 2. Gestión de Configuraciones y Seguridad

Para evitar la fuga accidental de credenciales en GitHub, la aplicación sigue el principio de *Doce Factores (12-Factor App)* mediante variables de entorno.

### 2.1. Configuración de Base de Datos (Supabase / PostgreSQL)
En `application.properties` se utilizan variables de entorno con valores por defecto para desarrollo:

```properties
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/deployops}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:postgres}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:postgres}
spring.datasource.driver-class-name=org.postgresql.Driver
```

* **En producción (Supabase):** Las variables `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` se configuran en el panel del servicio de hosting.
* **Cifrado de contraseñas:** Los passwords de usuarios se almacenan utilizando `BCryptPasswordEncoder` (nunca en texto plano).

---

## 3. Observabilidad y Monitoreo del Sistema

El propio sistema es observable para facilitar el diagnóstico de su operación:

* **Spring Boot Actuator:** Expone el endpoint `/actuator/health` para comprobar el estado interno del backend y su conexión a la base de datos PostgreSQL.
* **Logs Estructurados:** Los eventos críticos (apertura de incidentes, fallos reiterados en endpoints y errores de autenticación) se registran con formato uniforme, incluyendo marca de tiempo, nivel (`INFO`, `WARN`, `ERROR`), clase de origen y mensaje explicativo.

---

## 4. Despliegue de la Aplicación

La arquitectura está diseñada para desplegarse de manera ágil y desacoplada:

1. **Base de Datos:** Instancia gestionada de PostgreSQL en **Supabase** (permanente, con backups y disponible en la nube).
2. **Backend:** Empaquetado como archivo ejecutable `.jar` de Spring Boot, desplegable en servicios cloud (ej: Render, Railway o VPS).
3. **Frontend:** Compilación estática de Angular (`ng build`) servida mediante un servidor web liviano (Nginx, Vercel o Netlify).
