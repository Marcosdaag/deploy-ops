# DeployOps — Sistema de Diseno y Especificacion Visual (v1.0)

Este documento establece el contrato de diseno formal de **DeployOps**, basado en los principios de herramientas de ingenieria y observabilidad de produccion (GitHub Primer, Stripe, Linear y Datadog). 

Su proposito es erradicar patrones genericos de plantillas ("AI Slop" — degradados estridentes, neones artificiales, tarjetas flotantes identicas) y garantizar una interfaz de alta densidad de datos, ergonomia visual para guardias de ingenieria (SRE) y estabilidad de layout (Zero Cumulative Layout Shift).

---

## 1. Principios Fundamentales de Diseno

1. **Color Estrictamente Funcional:**
   * El 90% de la interfaz es neutra (grises, carbones y pizarras de alto descanso visual).
   * Los colores saturados se reservan **exclusivamente** para estados de salud de la red y telemetria:
     * **Verde (#3fb950 / #1a7f37):** Estado saludable (HTTP 2xx, 100% operativo).
     * **Ambar (#d29922 / #9a6700):** Estado degradado, flapping o latencia anomala (>500ms).
     * **Rojo (#f85149 / #cf222e):** Servicio caido (HTTP 5xx, timeout, incidente abierto).
     * **Cian (#38bdf8 / #0969da):** Servicio en proceso de recuperacion (fase de prueba de 2 checks exitosos).
2. **Precision Numerica y Tipografia Tabular:**
   * Todas las metricas dinamicas (latencia en ms, porcentaje de uptime, contadores y codigos HTTP) deben utilizar `font-variant-numeric: tabular-nums;` y fuentes monoespaciadas (`font-mono`).
   * Esto previene que las cifras "bailen" horizontalmente cuando el motor de Server-Sent Events (SSE) o el polling actualizan los datos en vivo.
3. **Alta Densidad de Informacion vs 'Cajitas' Flotantes:**
   * En lugar de llenar la pantalla con tarjetas cuadradas con sombras difusas, se priorizan tablas de datos estructuradas con micro-sparklines de 30 dias y divisores de 1px.
   * La arquitectura se presenta como un **Pipeline de Flujo Interactivo** en lugar de tarjetas aisladas de caracteristicas.
4. **Accesibilidad y Navegacion por Teclado (WCAG 2.1 AA):**
   * Anillos de foco visibles (`focus-visible`) de 2px con offset de 2px en todos los botones y campos de entrada.
   * Cierre con tecla `Escape` en modales y drawer lateral de telemetria.
   * Contraste minimo 4.5:1 para texto normal y 3:1 para texto grande o badges.

---

## 2. Tokens de Color

### Modo Oscuro (Predeterminado — Estilo GitHub Primer / Stripe Dark)

| Token | Valor Hex | Proposito |
| :--- | :--- | :--- |
| `--bg-canvas` | `#0d1117` | Fondo principal de la aplicacion (grafito profundo neutro). |
| `--bg-surface` | `#161b22` | Superficie de tablas, paneles y contenedores elevados. |
| `--bg-surface-alt` | `#21262d` | Fondo de filas alternadas, inputs y encabezados secundarios. |
| `--bg-surface-hover` | `#262c36` | Estado hover de filas y botones secundarios. |
| `--border-default` | `#30363d` | Bordes estructurales de tablas y separadores principales. |
| `--border-subtle` | `rgba(240, 246, 252, 0.1)` | Lineas divisorias internas y bordes sutiles. |
| `--text-primary` | `#f0f6fc` | Titulos, nombres de servicio y valores destacados. |
| `--text-secondary` | `#8b949e` | Descripciones, encabezados de columnas y etiquetas. |
| `--text-muted` | `#6e7681` | Metadatos secundarios, fechas y notas al pie. |
| `--accent-primary` | `#2f81f7` | Azul institucional tecnico para botones primarios y enlaces activos. |
| `--accent-primary-hover` | `#388bfd` | Estado hover del boton primario. |

### Modo Claro (Estilo Stripe / GitHub Light)

| Token | Valor Hex | Proposito |
| :--- | :--- | :--- |
| `--bg-canvas` | `#f6f8fa` | Fondo principal de la aplicacion. |
| `--bg-surface` | `#ffffff` | Fondo de tarjetas, tablas y modales. |
| `--bg-surface-alt` | `#f6f8fa` | Fondo de inputs y encabezados de tabla. |
| `--bg-surface-hover` | `#eaeef2` | Estado hover de filas. |
| `--border-default` | `#d0d7de` | Bordes estructurales de 1px. |
| `--border-subtle` | `#e1e4e8` | Lineas divisorias internas. |
| `--text-primary` | `#1f2328` | Titulos y contenido principal de alto contraste. |
| `--text-secondary` | `#656d76` | Descripciones y etiquetas. |
| `--text-muted` | `#8c959f` | Texto atenuado y metadatos. |
| `--accent-primary` | `#0969da` | Azul institucional para acciones principales. |

---

## 3. Estados Semanticos de Salud de Servicios

| Estado | Token Color | Fondo Subjetivo (Dark) | Texto y Borde (Dark) | Fondo (Light) | Texto (Light) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **OPERATIONAL (Healthy)** | `--status-healthy` | `rgba(46, 160, 67, 0.15)` | `#3fb950` | `#dafbe1` | `#1a7f37` |
| **DEGRADED (Flapping/Slow)**| `--status-degraded` | `rgba(187, 128, 9, 0.15)` | `#d29922` | `#fff8c5` | `#9a6700` |
| **DOWN (Incident)** | `--status-down` | `rgba(248, 81, 73, 0.15)` | `#f85149` | `#ffebe9` | `#cf222e` |
| **RECOVERING (Testing)** | `--status-recovering`| `rgba(56, 189, 248, 0.15)` | `#38bdf8` | `#ddf4ff` | `#0969da` |
| **PENDING (Queued)** | `--status-pending` | `rgba(110, 118, 129, 0.15)`| `#8b949e` | `#f6f8fa` | `#656d76` |

---

## 4. Tipografia y Jerarquia

* **Familia Sans-Serif Principal:** `Plus Jakarta Sans`, `-apple-system`, `BlinkMacSystemFont`, `'Segoe UI'`, `Roboto`, `sans-serif`.
* **Familia Monoespaciada Tecnica:** `ui-monospace`, `'JetBrains Mono'`, `'SF Mono'`, `Menlo`, `Consolas`, `monospace`.
* **Escala de Fuentes:**
  * Hero Display: `2.25rem` (36px), `font-weight: 700`, `letter-spacing: -0.03em`.
  * H2 Titulos de Seccion: `1.5rem` (24px), `font-weight: 600`, `letter-spacing: -0.02em`.
  * H3 Subtitulos / Modales: `1.125rem` (18px), `font-weight: 600`.
  * Cuerpo Base: `0.9375rem` (15px), `line-height: 1.55`.
  * Metricas y Codigos: `0.8125rem` (13px), `tabular-nums font-mono`.

---

## 5. Arquitectura del Layout

1. **TopBar Fija de Alta Densidad (60px):**
   * Logo y nombre de la plataforma alineados a la izquierda.
   * Navegacion tecnica en linea.
   * Indicador de latencia/conexion en tiempo real con el backend (`En linea` / `Conectando...`).
   * Acceso rapido a Status Page publica, Perfil y Selector de Tema.
2. **Hero Minimalista con Franja de Metricas Globales:**
   * Titulo y explicacion concisa.
   * **Strip de Salud Global:** Resumen en 4 bloques compactos (SLA Global 30d, Latencia Media p95, Total de Verificaciones y Estado del Sistema).
   * Boton primario CTA y boton secundario de exploracion.
3. **Monitor SRE de Alta Densidad (Tabla de Microservicios):**
   * Filtros de navegacion rapida (Todos, Saludables, Con Incidentes).
   * Tabla estructurada con columnas: Servicio, Estado, Historial SLA 30d (micro-barras con tooltip interactivo), Latencia actual en ms (`tabular-nums`), Frecuencia de chequeo y Acciones.
   * Al hacer clic en cualquier fila o en el boton de inspeccion, se abre el **Drawer lateral de telemetria**.
4. **Diagrama Interactivo del Pipeline de Datos (Datadog Style):**
   * Reemplazo completo de las cajitas flotantes de caracteristicas por un mapa interactivo de 5 etapas (Loom Scheduler -> Anti-SSRF Guard -> Finite State Machine -> PostgreSQL Window SLA -> SSE Reactive Stream).
5. **Consola Interactiva cURL & API Publica:**
   * Terminal en fondo negro grafito con pestañas para probar endpoints de la API publica con un solo clic.
