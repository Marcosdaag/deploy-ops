# REST API Specification

Este documento define los contratos de la API REST de **DeployOps**, detallando las rutas, métodos HTTP, estructura de datos JSON (Request/Response DTOs) y códigos de estado.

---

## 1. Convenciones Generales

* **Prefijo base:** `/api/v1`
* **Formato de datos:** `application/json` (UTF-8)
* **Formato de fechas:** ISO 8601 (`YYYY-MM-DDTHH:mm:ssZ`)

### Formato estándar de error
Toda respuesta de error (códigos 4xx y 5xx) sigue una estructura uniforme:

```json
{
  "timestamp": "2026-09-22T17:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "El campo 'endpointUrl' debe ser una URL válida",
  "path": "/api/v1/services"
}
```

---

## 2. Endpoints Públicos (Landing Page)

### 2.1. Obtener servicios públicos de demostración
* **Ruta:** `GET /api/v1/public/showcase`
* **Seguridad:** Público (sin autenticación).
* **Descripción:** Retorna el estado en vivo y latencia de los servicios de referencia (ej: Google, GitHub) para la landing.
* **Respuesta (`200 OK`):**
```json
[
  {
    "name": "Google Search API",
    "targetUrl": "https://www.google.com",
    "status": "HEALTHY",
    "lastLatencyMs": 45,
    "lastCheckedAt": "2026-09-22T17:05:00Z",
    "uptimePercentage": 99.98
  },
  {
    "name": "GitHub Status",
    "targetUrl": "https://www.githubstatus.com",
    "status": "HEALTHY",
    "lastLatencyMs": 82,
    "lastCheckedAt": "2026-09-22T17:05:12Z",
    "uptimePercentage": 99.95
  }
]
```

---

## 3. Autenticación y Cuentas

### 3.1. Registro de usuario
* **Ruta:** `POST /api/v1/auth/register`
* **Petición:**
```json
{
  "email": "usuario@ejemplo.com",
  "password": "PasswordSegura123!"
}
```
* **Respuesta:** `201 Created`

### 3.2. Inicio de sesión
* **Ruta:** `POST /api/v1/auth/login`
* **Petición:**
```json
{
  "email": "usuario@ejemplo.com",
  "password": "PasswordSegura123!"
}
```
* **Respuesta (`200 OK`):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsIn...",
  "type": "Bearer",
  "expiresIn": 86400
}
```

---

## 4. Gestión de Servicios y Entornos

### 4.1. Listar servicios del usuario
* **Ruta:** `GET /api/v1/services`
* **Cabecera requerida:** `Authorization: Bearer <token>`
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": "e4b2d184-7a32-4721-a5c9-2ef4d5b248a1",
    "name": "Orders API",
    "repositoryUrl": "https://github.com/empresa/orders-api",
    "environment": "PRODUCTION",
    "status": "HEALTHY",
    "lastLatencyMs": 142,
    "activeIncidentId": null,
    "lastCheckedAt": "2026-09-22T17:04:30Z"
  }
]
```

### 4.2. Crear nuevo servicio
* **Ruta:** `POST /api/v1/services`
* **Petición:**
```json
{
  "name": "Payment Gateway",
  "repositoryUrl": "https://github.com/empresa/payments",
  "environment": "PRODUCTION",
  "endpointUrl": "https://api.empresa.com/health",
  "httpMethod": "GET",
  "checkIntervalSeconds": 30,
  "timeoutSeconds": 5,
  "expectedStatusCode": 200
}
```
* **Respuesta:** `201 Created` con el recurso creado.

### 4.3. Detalle de un servicio específico
* **Ruta:** `GET /api/v1/services/{id}`
* **Respuesta (`200 OK`):** Devuelve la configuración completa del servicio, configuración de sondeo y estado actual.

---

## 5. Histórico de Comprobaciones (Métricas)

### 5.1. Consultar resultados históricos de comprobación
* **Ruta:** `GET /api/v1/services/{id}/checks?page=0&size=50`
* **Descripción:** Proporciona los puntos de datos para graficar la latencia y disponibilidad a lo largo del tiempo.
* **Respuesta (`200 OK`):**
```json
{
  "content": [
    {
      "id": "1",
      "statusCode": 200,
      "latencyMs": 138,
      "status": "UP",
      "checkedAt": "2026-09-22T17:04:30Z"
    },
    {
      "id": "2",
      "statusCode": 500,
      "latencyMs": 850,
      "status": "DOWN",
      "checkedAt": "2026-09-22T17:04:00Z"
    }
  ],
  "totalElements": 240,
  "totalPages": 5,
  "currentPage": 0
}
```

---

## 6. Incidentes

### 6.1. Listar incidentes
* **Ruta:** `GET /api/v1/incidents?state=OPEN`
* **Parámetros opcionales:** `state` (`OPEN`, `RESOLVED`), `serviceId`.
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": "42",
    "serviceId": "e4b2d184-7a32-4721-a5c9-2ef4d5b248a1",
    "serviceName": "Orders API",
    "environment": "PRODUCTION",
    "severity": "CRITICAL",
    "state": "OPEN",
    "failureReason": "HTTP 500 detectado en 3 checks consecutivos",
    "startedAt": "2026-09-22T17:00:00Z",
    "resolvedAt": null,
    "durationMinutes": null
  }
]
```

---

## 7. Despliegues (Releases)

### 7.1. Registrar un despliegue
* **Ruta:** `POST /api/v1/services/{id}/deployments`
* **Descripción:** Endpoint preparado para invocación manual o integración futura con CI/CD (GitHub Actions).
* **Petición:**
```json
{
  "version": "v2.8.1",
  "commitHash": "a81bc9f3d",
  "environment": "PRODUCTION",
  "deployedAt": "2026-09-22T16:50:00Z"
}
```
* **Respuesta:** `201 Created`
