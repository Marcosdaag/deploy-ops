# Domain and Business Rules

Este documento define la lógica de negocio, el ciclo operativo central y las reglas que gobiernan el comportamiento del sistema en **DeployOps**.

---

## 1. El Ciclo Operativo (Core Operational Loop)

La plataforma conecta el estado del software con su ciclo de despliegue mediante un bucle cerrado de cinco etapas consecutivas:

```
[ 1. Servicio ] ──> [ 2. Despliegue ] ──> [ 3. Health Check ] ──> [ 4. Incidente ] ──> [ 5. Recuperación ]
```

### Detalle de las etapas

1. **Servicio y Entorno:** Representa una API o componente de software registrado en el sistema bajo un entorno determinado (ej: `Production`, `Staging`). Cada servicio tiene asociado un endpoint de verificación (URL, método y timeout).
2. **Despliegue (Release):** Registro del cambio de versión (`version`, `commit hash`, `timestamp`). Permite correlacionar anomalías de salud con eventos de despliegue específicos.
3. **Health Check:** Ejecución periódica y programada de una sonda HTTP contra el endpoint configurado, evaluando el código de respuesta y midiendo la latencia en milisegundos.
4. **Incidente:** Apertura automática de un registro de interrupción operativa cuando los fallos acumulados superan la política de umbrales definida.
5. **Recuperación:** Cierre automático del incidente una vez que el servicio demuestra estabilidad continua, registrando la duración total de la caída (*Time to Recovery*).

### Ejemplo práctico de flujo completo

1. Se registra el servicio `orders-service` en entorno `Production` con check a `GET /health` cada 30 segundos.
2. Se notifica un despliegue de la versión `v2.8.1` (commit `a81bc9`).
3. El scheduler ejecuta comprobaciones regulares obteniendo `HTTP 200` y latencia promedio de 140 ms.
4. El endpoint comienza a responder `HTTP 500`. Tras 3 fallos consecutivos, el servicio pasa a estado `DOWN` y se abre automáticamente el `Incidente #42`.
5. Angular recibe la actualización de estado en tiempo real.
6. El servicio vuelve a responder `HTTP 200`. Al acumular 2 respuestas exitosas seguidas, el incidente se marca como `RESOLVED` con una duración total calculada (ej: 4 minutos).

---

## 2. Módulos Funcionales

* **Catálogo de Servicios:** Administra las aplicaciones monitoreadas, sus metadatos (repositorio, tags, propietario) y sus entornos (`Development`, `Staging`, `Production`).
* **Motor de Monitoreo:** Gestiona la configuración de los health checks (frecuencia, timeouts, cabeceras) y registra el histórico de cada ejecución.
* **Gestión de Incidentes:** Registra el ciclo de vida de las interrupciones operativas, su severidad (`LOW`, `MEDIUM`, `CRITICAL`), la línea de tiempo de eventos y la versión de software asociada.
* **Espacio Público (Landing Page):** Expone un panel de solo lectura con comprobaciones en vivo de servicios públicos conocidos (ej: APIs de Google, GitHub) para demostrar el funcionamiento de la plataforma sin requerir inicio de sesión previo.
* **Espacio Privado (Workspaces):** Permite a los usuarios registrados gestionar de forma aislada sus propios servicios, credenciales y alertas.

---

## 3. Reglas de Negocio

Para asegurar que la plataforma sea fiable y evitar ruido innecesario, se implementan las siguientes reglas de negocio:

### 3.1. Detección de Incidentes por Umbrales (Evitar falsos positivos)
* **Regla:** Un incidente **no** se abre ante un único fallo aislado (errores transitorios de red, reinicios breves).
* **Política:** El servicio pasa a estado `DOWN` y se abre un incidente únicamente tras **3 fallos consecutivos** en las comprobaciones de salud.
* **Estado intermedio:** Entre el primer y el segundo fallo consecutivo, el servicio se marca como `DEGRADED`, alertando de una posible anomalía sin abrir todavía un incidente formal.

### 3.2. Criterio de Recuperación Estable
* **Regla:** Un incidente **no** se cierra ante la primera respuesta exitosa (`HTTP 200`).
* **Política:** La recuperación requiere **2 comprobaciones exitosas consecutivas**. Esto previene el parpadeo de estado (*flapping*) en servicios inestables que alternan respuestas correctas con errores.
* Al confirmarse la recuperación, el incidente pasa a estado `RESOLVED` y se calcula la métrica de tiempo total de resolución.

### 3.3. Idempotencia en Despliegues
* **Regla:** La ingesta repetida de un mismo webhook o notificación de release no debe duplicar registros.
* **Política:** Se utiliza una clave de idempotencia compuesta por `service_id` + `environment` + `commit_hash`. Si se recibe un evento duplicado, se actualiza el estado del despliegue existente sin crear uno nuevo.

### 3.4. Aislamiento de Datos por Usuario
* **Regla:** Cada usuario autenticado tiene acceso estricto únicamente a los servicios y configuraciones de su propio espacio de trabajo.
* **Excepción:** La landing page pública contiene endpoints de demostración globales gestionados por el sistema, aislados de las cuentas de usuario.

---

## 4. Máquina de Estados del Servicio e Incidente

El ciclo de estados para cualquier servicio registrado sigue la siguiente transición:

```
                  ┌────────────────────────────────────────┐
                  │                                        │
                  ▼                                        │ 2 éxitos
            +-----------+        1 fallo        +------------+ consecutivos
            |  HEALTHY  | ────────────────────> |  DEGRADED  |
            +-----------+                       +------------+
                  ▲                                    │
                  │                                    │ 3 fallos consecutivos
                  │ 2 éxitos consecutivos              ▼
            +-----------+                     +------------------+
            | RECOVERING| <────────────────── |  DOWN / INCIDENT |
            +-----------+       1er éxito     +------------------+
```

| Estado | Condición | Acción en el sistema |
| :--- | :--- | :--- |
| `HEALTHY` | Checks respondiendo con código esperado | Funcionamiento normal. |
| `DEGRADED` | 1 o 2 fallos consecutivos detectados | Advertencia visual; monitoreo intensificado. |
| `DOWN` | 3 fallos consecutivos acumulados | Se abre un `Incident` con estado `OPEN` y se notifica al frontend. |
| `RECOVERING` | 1er check exitoso tras estar en `DOWN` | Período de validación; el incidente permanece abierto. |
| `RESOLVED` | 2do check exitoso consecutivo | El incidente cambia a `RESOLVED` y el servicio vuelve a `HEALTHY`. |

---

## 5. Esquema de Entidades (Conceptual)

Las entidades fundamentales para soportar este dominio son:

* **`User`:** Identificador, email, password cifrada, rol y fecha de creación.
* **`Service`:** Nombre del servicio, repositorio, tags y usuario propietario.
* **`Environment`:** Nombre del entorno (`Production`, `Staging`), URL base y referencia al servicio.
* **`HealthCheckConfig`:** Endpoint relativo, método HTTP, frecuencia en segundos, timeout y código de respuesta esperado.
* **`CheckResult`:** Código HTTP recibido, tiempo de respuesta (ms), estado obtenido (`UP`, `DOWN`), marca de tiempo y referencia al entorno.
* **`Incident`:** Título, severidad, estado (`OPEN`, `RESOLVED`), marca de tiempo de inicio, marca de tiempo de resolución y versión de release asociada.
* **`Deployment`:** Versión semántica, commit hash, fecha de despliegue y estado de la release.

> *Nota: El Diagrama Entidad-Relación (ERD) detallado con claves primarias, foráneas e índices se incorporará en este apartado.*
