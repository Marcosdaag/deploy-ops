import { EndpointResponse, ServiceStatus } from '../models/endpoint.model';

export interface QuickPreset {
  label: string;
  name: string;
  url: string;
  isSecurityTest?: boolean;
}

export interface StateNodeInfo {
  status: ServiceStatus;
  title: string;
  tag: string;
  rule: string;
  description: string;
  trigger: string;
}

/*
 * Datos iniciales que mostramos al instante mientras el backend en Render
 * responde la primera consulta, asi la pagina nunca arranca vacia.
 */
export const INITIAL_SHOWCASE_ENDPOINTS: EndpointResponse[] = [
  {
    id: 2,
    name: 'Google Search',
    url: 'https://www.google.com',
    status: 'HEALTHY',
    consecutiveFailures: 0,
    lastResponseTimeMs: 69,
    lastCheckedAt: new Date().toISOString(),
    createdAt: new Date().toISOString(),
    uptimePercentage: 100.0
  },
  {
    id: 4,
    name: 'Cloudflare Edge DNS',
    url: 'https://1.1.1.1',
    status: 'HEALTHY',
    consecutiveFailures: 0,
    lastResponseTimeMs: 58,
    lastCheckedAt: new Date().toISOString(),
    createdAt: new Date().toISOString(),
    uptimePercentage: 100.0
  }
];

/*
 * Accesos rapidos para que cualquiera que visite la pagina pueda probar
 * el monitor con un clic, incluyendo un caso para disparar el filtro anti-SSRF.
 */
export const QUICK_PRESETS: QuickPreset[] = [
  { label: 'Wikipedia', name: 'Wikimedia Global Portal', url: 'https://www.wikipedia.org' },
  { label: 'Google', name: 'Google Search', url: 'https://www.google.com' },
  { label: 'Cloudflare', name: 'Cloudflare Edge DNS', url: 'https://1.1.1.1' },
  {
    label: 'Simular bloqueo Anti-SSRF (localhost)',
    name: 'Internal Loopback',
    url: 'http://localhost:8080/admin',
    isSecurityTest: true
  }
];

/*
 * Explicacion paso a paso de nuestra maquina de estados. Sirve para mostrar
 * en la landing como evitamos falsas alarmas ante micro-cortes de red.
 */
export const STATE_MACHINE_STEPS: StateNodeInfo[] = [
  {
    status: 'PENDING',
    title: 'Pending',
    tag: 'Estado Inicial',
    rule: '0 ejecuciones previas',
    description:
      'El servicio pasa las validaciones de formato y el escudo anti-SSRF. Queda guardado en PostgreSQL esperando la siguiente vuelta del scheduler.',
    trigger: 'POST /api/endpoints → Registro inmediato'
  },
  {
    status: 'HEALTHY',
    title: 'Healthy',
    tag: 'Operación Nominal',
    rule: 'HTTP 2xx en menos de 5s',
    description:
      'El endpoint responde en tiempo y forma. Guardamos su latencia en milisegundos y mantenemos su porcentaje de disponibilidad al 100%.',
    trigger: 'Respuesta 2xx o 2 éxitos seguidos tras caída'
  },
  {
    status: 'DEGRADED',
    title: 'Degraded',
    tag: 'Amortiguación de Fallos',
    rule: '1 o 2 fallos seguidos',
    description:
      'Si un servidor tiene un hipo aislado de red, lo marcamos en alerta temprana pero todavía no abrimos un incidente para no generar falsas alarmas.',
    trigger: 'Timeout (>5s) o error HTTP 4xx/5xx (intento 1 o 2)'
  },
  {
    status: 'DOWN',
    title: 'Down',
    tag: 'Incidente Confirmado',
    rule: '3 o más fallos seguidos',
    description:
      'Recién al tercer fallo consecutivo confirmamos que el servicio cayó de verdad. Se abre un incidente activo en la base que empieza a descontar SLA.',
    trigger: 'Tercer fallo consecutivo confirmado'
  },
  {
    status: 'RECOVERING',
    title: 'Recovering',
    tag: 'Estabilización',
    rule: '1er éxito luego de caída',
    description:
      'Cuando un servicio caído vuelve a responder bien por primera vez, queda a prueba un ciclo más. Si confirma un segundo éxito, cerramos el incidente.',
    trigger: 'Primera respuesta HTTP 2xx después de estar caído'
  }
];
