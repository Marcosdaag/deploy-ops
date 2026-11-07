# Referencia Completa de la API REST — DeployOps v2.0 Enterprise

Catálogo exhaustivo de los 33 endpoints disponibles en DeployOps v2.0 (21 consolidados en v1.0 + 12 incorporados en la arquitectura distribuida v2.0).

---

## 1. Autenticación y Gestión de Cuentas (5 Endpoints)

| Método | Ruta | Autenticación | Cuota Rate Limit | Descripción |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Pública | 5 req / 60s | Registro de cuenta con email, nombre y contraseña cifrada en BCrypt. Retorna token JWT. |
| `POST` | `/api/auth/login` | Pública | 10 req / 60s | Inicio de sesión con credenciales. Emite token JWT con 24h de vigencia. |
| `POST` | `/api/auth/google` | Pública | 10 req / 60s | Validación criptográfica de ID Token de Google OAuth2 e inicio de sesión. |
| `POST` | `/api/auth/demo` | Pública | 15 req / 60s | Acceso instantáneo en un clic a sesión SRE de demostración (`demo@deployops.io`). |
| `GET` | `/api/auth/me` | Bearer JWT | Sin cuota fija | Retorna perfil del operador autenticado, rol y `publicSlug`. |

---

## 2. Gestión de Endpoints y Telemetría SRE (14 Endpoints)

| Método | Ruta | Autenticación | Cuota Rate Limit | Descripción |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/endpoints` | Opcional (Filtra por scope) | Sin cuota | Lista endpoints. Parámetro `scope=mine` (propios), `scope=showcase` (públicos). |
| `POST` | `/api/endpoints` | Bearer JWT | 15 req / 60s | Alta de nuevo servicio con validación perimetral Anti-SSRF. Inicia en `PENDING`. |
| `POST` | `/api/endpoints/samples` | Bearer JWT | 10 req / 60s | Siembra automática de endpoints de prueba para cuentas recién creadas. |
| `PUT` | `/api/endpoints/{id}` | Bearer JWT (Owner) | 20 req / 60s | Modificación de nombre, URL, método HTTP, código esperado y headers. |
| `DELETE` | `/api/endpoints/{id}` | Bearer JWT (Owner) | 20 req / 60s | Eliminación del servicio y baja en cascada de logs e incidentes asociados. |
| `PATCH` | `/api/endpoints/{id}/visibility` | Bearer JWT (Owner) | 30 req / 60s | Conmuta visibilidad pública para inclusión en Status Page (`isPublicStatus`). |
| `POST` | `/api/endpoints/{id}/ping` | Pública / Token | 20 req / 60s | Disparo manual de sondeo HTTP inmediato ejecutado en hilo virtual (Loom). |
| `POST` | `/api/endpoints/{id}/test-alert` | Bearer JWT (Owner) | 5 req / 60s | Disparo de alerta SMTP de prueba a la casilla de correo del operador. |
| `GET` | `/api/endpoints/{id}/incidents` | Opcional / Owner | Sin cuota | Listado histórico de incidentes (`ONGOING` y `RESOLVED`) del servicio. |
| `GET` | `/api/endpoints/{id}/metrics` | Opcional / Owner | Sin cuota | Series temporales de latencia y disponibilidad (`range=6h, 24h, 7d, 30d`). |
| `GET` | `/api/endpoints/{id}/report` | Bearer JWT (Owner) | Sin cuota | Reporte mensual agregado de disponibilidad SLA y conteo de incidentes en JSON. |
| `GET` | `/api/endpoints/{id}/report/csv` | Bearer JWT (Owner) | 10 req / 60s | Descarga de reporte de disponibilidad en formato CSV estructurado. |
| `POST` | `/api/endpoints/{id}/report/email` | Bearer JWT (Owner) | 5 req / 60s | Envío del reporte mensual de disponibilidad SLA adjunto por correo electrónico. |
| `GET` | `/api/endpoints/stream` | Pública | 1 conexión / cliente | Canal Server-Sent Events (SSE) con actualizaciones en tiempo real del dashboard. |

---

## 3. Páginas de Estado Públicas (Status Pages) (2 Endpoints)

| Método | Ruta | Autenticación | Cuota Rate Limit | Descripción |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/status/{slug}` | Pública | Caché L2 Redis (60s) | Retorna estado agregado del tenant, servicios públicos y SLA a 30 días. |
| `PATCH` | `/api/status/slug` | Bearer JWT | 5 req / 60s | Actualiza el slug personalizado del operador (ej. `/status/acme-corp`). Invalida caché L2. |

---

## 4. Webhooks y Mensajería Criptográfica (8 Endpoints v2.0)

| Método | Ruta | Autenticación | Cuota Rate Limit | Descripción |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/webhooks` | Bearer JWT | Sin cuota | Lista canales de alerta del usuario con secreto enmascarado (`dops_sec_••••••••4f9a`). |
| `POST` | `/api/webhooks` | Bearer JWT | 15 req / 60s | Alta de canal (Slack/Discord/Custom). Retorna `rawSecretOnce` una única vez. |
| `PATCH` | `/api/webhooks/{id}` | Bearer JWT (Owner) | 20 req / 60s | Modificación de nombre, URL destino, proveedor o activación/pausa. |
| `DELETE` | `/api/webhooks/{id}` | Bearer JWT (Owner) | 20 req / 60s | Eliminación del webhook y desvinculación de auditorías. |
| `POST` | `/api/webhooks/{id}/rotate-secret` | Bearer JWT (Owner) | 5 req / 60s | Rotación criptográfica inmediata del secreto HMAC-SHA256. Retorna nuevo secreto una vez. |
| `POST` | `/api/webhooks/{id}/test` | Bearer JWT (Owner) | 10 req / 60s | Envío de evento de prueba `WEBHOOK_TEST_PING` con firma criptográfica. |
| `GET` | `/api/webhooks/failures` | Bearer JWT | Sin cuota | Lista entregas fallidas derivadas a Dead Letter Queue (`webhooks.dlq`). |
| `POST` | `/api/webhooks/failures/{id}/retry` | Bearer JWT (Owner) | 10 req / 60s | Reintento manual firmado de un mensaje fallido. Si tiene éxito marca `RETRIED_OK`. |

---

## 5. Telemetría Distribuida y Caos SRE (4 Endpoints v2.0)

| Método | Ruta | Autenticación | Cuota Rate Limit | Descripción |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/distributed/overview` | Pública | Sin cuota | Indicadores KPI del clúster (eventos Outbox, hits/misses de caché L2, estado Redis/AMQP). |
| `GET` | `/api/chaos/state` | Pública / Token | Sin cuota | Consulta el código HTTP simulado actual del sandbox (`200`, `503`, `504`, `401`). |
| `POST` | `/api/chaos/state` | Pública / Token | 20 req / 60s | Conmuta el código HTTP simulado del sandbox para pruebas de contingencia. |
| `POST` | `/api/chaos/trigger` | Pública / Token | 10 req / 60s | Dispara un evento `CHAOS_SIMULATION` para observar en vivo el retroceso y desvío a DLQ. |
