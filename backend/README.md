# DeployOps - Backend Core

Motor de observabilidad y monitoreo concurrente desarrollado con **Java 21**, **Spring Boot 4.1.1** y **Virtual Threads (Project Loom)**.

## Características Técnicas

- **Concurrencia:** Hilos virtuales de Java 21 (`Executors.newVirtualThreadPerTaskExecutor()`) para sondeos HTTP sin bloqueo de sockets del sistema operativo.
- **Seguridad Perimetral:** `UrlSecurityValidator` defensivo contra Server-Side Request Forgery (SSRF) con resolución DNS, filtrado de subredes privadas RFC 1918, direcciones loopback, metadatos cloud y puertos sensibles.
- **Control de Flapping:** Máquina de estados finitos (3 confirmaciones consecutivas de fallo para declarar `DOWN`, 2 de recuperación para volver a `HEALTHY`).
- **Disponibilidad SLA:** Cálculo de disponibilidad en ventana móvil de 30 días sin problema N+1 mediante consulta JPQL en lote.
- **Canal Reactivo:** Transmisión de métricas en tiempo real a clientes web a través de Server-Sent Events (SSE).
- **Persistencia:** PostgreSQL 16 con Spring Data JPA y soporte para base de datos en memoria H2 en perfiles de test.

## Requisitos

- Java Development Kit (JDK) 21 LTS
- Docker y Docker Compose (para base de datos PostgreSQL)
- Maven 3.9+ (o `./mvnw` incluido)

## Comandos de Desarrollo

### 1. Iniciar Base de Datos Local
```bash
docker compose -f ../docker-compose.yml up -d
```

### 2. Ejecutar Pruebas Automatizadas
Ejecución de las 116 pruebas unitarias y de integración con JUnit 5:
```bash
./mvnw test
```

### 3. Iniciar la Aplicación
```bash
./mvnw spring-boot:run
```
La API estará disponible en `http://localhost:8080`.
