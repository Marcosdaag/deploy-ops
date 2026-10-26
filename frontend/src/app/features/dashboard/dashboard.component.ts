import {
  Component,
  HostListener,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import {
  DashboardMetrics,
  EndpointCreateRequest,
  EndpointResponse,
  ServiceStatus
} from '../../core/models/endpoint.model';
import {
  INITIAL_SHOWCASE_ENDPOINTS,
  QUICK_PRESETS,
  QuickPreset,
  STATE_MACHINE_STEPS,
  StateNodeInfo
} from '../../core/constants/showcase.constants';
import { EndpointService } from '../../core/services/endpoint.service';

type StatusFilter = 'ALL' | 'HEALTHY' | 'ALERT' | 'PENDING';
type ThemeMode = 'dark' | 'light';
type ApiMethodTab = 'GET' | 'POST' | 'DELETE';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit, OnDestroy {
  readonly endpointService = inject(EndpointService);

  /*
   * Estado general de la pagina: guardamos la lista de endpoints que vienen de Render,
   * el tema elegido (oscuro o claro) y la posicion del mouse para el brillo suave del fondo.
   */
  readonly endpoints = signal<EndpointResponse[]>(INITIAL_SHOWCASE_ENDPOINTS);
  readonly isSyncing = signal<boolean>(false);
  readonly lastSyncedAt = signal<Date | null>(null);
  readonly theme = signal<ThemeMode>('dark');

  readonly isScrolled = signal<boolean>(false);
  readonly showScrollTop = signal<boolean>(false);
  readonly cursorX = signal<number>(-500);
  readonly cursorY = signal<number>(-500);

  /*
   * Estado para los controles interactivos: filtros del listado, formulario para
   * dar de alta nuevos servicios, panel lateral de detalle y explorador de la API.
   */
  readonly searchQuery = signal<string>('');
  readonly statusFilter = signal<StatusFilter>('ALL');

  readonly newServiceName = signal<string>('');
  readonly newServiceUrl = signal<string>('');
  readonly isSubmittingCreate = signal<boolean>(false);
  readonly createErrors = signal<string[]>([]);

  readonly selectedEndpoint = signal<EndpointResponse | null>(null);
  readonly endpointToDelete = signal<EndpointResponse | null>(null);
  readonly isDeleting = signal<boolean>(false);

  readonly activeStateNode = signal<ServiceStatus>('HEALTHY');
  readonly activeApiTab = signal<ApiMethodTab>('GET');
  readonly liveJsonOutput = signal<string>('');
  readonly isTestingPublicApi = signal<boolean>(false);
  readonly apiLastLatencyMs = signal<number | null>(null);
  readonly apiPulseActive = signal<boolean>(false);

  readonly toast = signal<{ type: 'success' | 'error'; message: string } | null>(null);
  readonly nowTick = signal<number>(Date.now());

  readonly quickPresets = QUICK_PRESETS;
  readonly stateNodes = STATE_MACHINE_STEPS;

  private refreshTimerId: ReturnType<typeof setInterval> | null = null;
  private clockTimerId: ReturnType<typeof setInterval> | null = null;
  private toastTimeoutId: ReturnType<typeof setTimeout> | null = null;
  private hasAttemptedAutoSeed = false;

  /*
   * Propiedades calculadas (computed): se actualizan solas cada vez que llega una
   * respuesta nueva del backend. Arman los dos ejemplos principales, el resumen de
   * metricas globales y la lista filtrada.
   */
  readonly featuredPair = computed<EndpointResponse[]>(() => {
    const list = this.endpoints();
    if (list.length >= 2) return list.slice(0, 2);
    if (list.length === 1) return [list[0], INITIAL_SHOWCASE_ENDPOINTS[1]];
    return INITIAL_SHOWCASE_ENDPOINTS;
  });

  readonly metrics = computed<DashboardMetrics>(() => {
    const list = this.endpoints();
    const total = list.length;
    const healthy = list.filter((e) => e.status === 'HEALTHY').length;
    const degraded = list.filter((e) => e.status === 'DEGRADED').length;
    const down = list.filter((e) => e.status === 'DOWN').length;
    const pending = list.filter((e) => e.status === 'PENDING').length;
    const recovering = list.filter((e) => e.status === 'RECOVERING').length;

    const checked = list.filter((e) => e.status !== 'PENDING');
    const globalUptime =
      checked.length > 0
        ? checked.reduce((acc, item) => acc + item.uptimePercentage, 0) / checked.length
        : 100;

    const withLatency = list.filter(
      (e) => e.lastResponseTimeMs !== null && e.lastResponseTimeMs > 0 && e.status !== 'DOWN'
    );
    const averageLatencyMs =
      withLatency.length > 0
        ? Math.round(
            withLatency.reduce((acc, item) => acc + (item.lastResponseTimeMs ?? 0), 0) /
              withLatency.length
          )
        : null;

    return {
      total,
      healthy,
      degraded,
      down,
      pending,
      recovering,
      globalUptime,
      averageLatencyMs
    };
  });

  readonly filteredEndpoints = computed<EndpointResponse[]>(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const filter = this.statusFilter();

    return this.endpoints().filter((item) => {
      const matchesQuery =
        !query ||
        item.name.toLowerCase().includes(query) ||
        item.url.toLowerCase().includes(query);

      if (!matchesQuery) return false;
      if (filter === 'HEALTHY') return item.status === 'HEALTHY';
      if (filter === 'ALERT') return item.status === 'DOWN' || item.status === 'DEGRADED';
      if (filter === 'PENDING') return item.status === 'PENDING' || item.status === 'RECOVERING';
      return true;
    });
  });

  readonly currentStateInfo = computed<StateNodeInfo>(() => {
    const current = this.activeStateNode();
    return this.stateNodes.find((n) => n.status === current) ?? this.stateNodes[1];
  });

  /*
   * Eventos de ventana y ciclo de vida: escuchamos el movimiento del cursor para la
   * luz sutil del fondo, controlamos el scroll para el menu superior y dejamos corriendo
   * un refresco automatico cada 25 segundos contra Render.
   */
  @HostListener('document:mousemove', ['$event'])
  onMouseMove(event: MouseEvent): void {
    this.cursorX.set(event.clientX);
    this.cursorY.set(event.clientY);
  }

  @HostListener('window:scroll')
  onWindowScroll(): void {
    const y = window.scrollY || document.documentElement.scrollTop || 0;
    this.isScrolled.set(y > 20);
    this.showScrollTop.set(y > 460);
  }

  scrollToTop(event?: Event): void {
    if (event) event.preventDefault();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  ngOnInit(): void {
    this.initTheme();
    this.onWindowScroll();
    this.syncWithPublicApi();

    this.refreshTimerId = setInterval(() => {
      if (!this.isSyncing()) {
        this.syncWithPublicApi();
      }
    }, 25000);

    this.clockTimerId = setInterval(() => {
      this.nowTick.set(Date.now());
    }, 5000);
  }

  ngOnDestroy(): void {
    if (this.refreshTimerId) clearInterval(this.refreshTimerId);
    if (this.clockTimerId) clearInterval(this.clockTimerId);
    if (this.toastTimeoutId) clearTimeout(this.toastTimeoutId);
  }

  private initTheme(): void {
    const saved = localStorage.getItem('deployops_landing_theme') as ThemeMode | null;
    const initial: ThemeMode = saved === 'light' ? 'light' : 'dark';
    this.theme.set(initial);
    document.documentElement.setAttribute('data-theme', initial);
  }

  toggleTheme(): void {
    const next: ThemeMode = this.theme() === 'dark' ? 'light' : 'dark';
    this.theme.set(next);
    localStorage.setItem('deployops_landing_theme', next);
    document.documentElement.setAttribute('data-theme', next);
  }

  /*
   * Comunicacion con el backend en Render: traemos los servicios activos, volvemos
   * a cargar los dos ejemplos base si alguien vacio la base de datos y manejamos
   * las altas y bajas de endpoints.
   */
  syncWithPublicApi(): void {
    this.isSyncing.set(true);
    const startMs = performance.now();

    this.endpointService.getEndpoints().subscribe({
      next: (data) => {
        if (data.length === 0 && !this.hasAttemptedAutoSeed) {
          this.hasAttemptedAutoSeed = true;
          this.seedDefaultShowcaseEndpoints();
          return;
        }

        this.apiLastLatencyMs.set(Math.round(performance.now() - startMs));
        this.endpoints.set(data);
        this.lastSyncedAt.set(new Date());
        this.isSyncing.set(false);
        this.liveJsonOutput.set(JSON.stringify(data, null, 2));

        const currentSelected = this.selectedEndpoint();
        if (currentSelected) {
          const updated = data.find((item) => item.id === currentSelected.id) ?? null;
          this.selectedEndpoint.set(updated);
        }
      },
      error: () => {
        this.isSyncing.set(false);
      }
    });
  }

  private seedDefaultShowcaseEndpoints(): void {
    forkJoin([
      this.endpointService.createEndpoint({
        name: 'Google Search',
        url: 'https://www.google.com'
      }),
      this.endpointService.createEndpoint({
        name: 'Cloudflare Edge DNS',
        url: 'https://1.1.1.1'
      })
    ]).subscribe({
      next: (createdPair) => {
        this.endpoints.set(createdPair);
        this.lastSyncedAt.set(new Date());
        this.isSyncing.set(false);
        this.liveJsonOutput.set(JSON.stringify(createdPair, null, 2));
      },
      error: () => {
        this.isSyncing.set(false);
      }
    });
  }

  applyPreset(preset: QuickPreset): void {
    this.newServiceName.set(preset.name);
    this.newServiceUrl.set(preset.url);
    this.createErrors.set([]);
  }

  clearCreateErrors(): void {
    this.createErrors.set([]);
  }

  submitCreateEndpoint(): void {
    const name = this.newServiceName().trim();
    const url = this.newServiceUrl().trim();

    const localErrors: string[] = [];
    if (!name) localErrors.push('Ingresa un nombre para identificar el servicio.');
    if (!url) localErrors.push('Ingresa la URL HTTP o HTTPS que queres monitorear.');

    if (localErrors.length > 0) {
      this.createErrors.set(localErrors);
      return;
    }

    this.isSubmittingCreate.set(true);
    this.createErrors.set([]);

    const payload: EndpointCreateRequest = { name, url };

    this.endpointService.createEndpoint(payload).subscribe({
      next: (created) => {
        this.endpoints.update((prev) => [created, ...prev]);
        this.newServiceName.set('');
        this.newServiceUrl.set('');
        this.isSubmittingCreate.set(false);
        this.liveJsonOutput.set(JSON.stringify(this.endpoints(), null, 2));
        this.showToast('success', `${created.name} sumado al monitor`);
      },
      error: (err) => {
        this.createErrors.set(this.endpointService.extractErrorMessages(err));
        this.isSubmittingCreate.set(false);
      }
    });
  }

  confirmDelete(endpoint: EndpointResponse, event?: Event): void {
    if (event) event.stopPropagation();
    this.endpointToDelete.set(endpoint);
  }

  cancelDelete(): void {
    if (this.isDeleting()) return;
    this.endpointToDelete.set(null);
  }

  executeDelete(): void {
    const target = this.endpointToDelete();
    if (!target) return;

    this.isDeleting.set(true);
    this.endpointService.deleteEndpoint(target.id).subscribe({
      next: () => {
        this.endpoints.update((prev) => prev.filter((item) => item.id !== target.id));
        if (this.selectedEndpoint()?.id === target.id) {
          this.selectedEndpoint.set(null);
        }
        this.isDeleting.set(false);
        this.endpointToDelete.set(null);
        this.liveJsonOutput.set(JSON.stringify(this.endpoints(), null, 2));
        this.showToast('success', `${target.name} quitado del listado`);
      },
      error: (err) => {
        const msg = this.endpointService.extractErrorMessages(err)[0];
        this.isDeleting.set(false);
        this.endpointToDelete.set(null);
        this.showToast('error', msg);
      }
    });
  }

  openInspector(endpoint: EndpointResponse): void {
    this.selectedEndpoint.set(endpoint);
  }

  closeInspector(): void {
    this.selectedEndpoint.set(null);
  }

  triggerPublicApiTest(): void {
    this.isTestingPublicApi.set(true);
    this.apiPulseActive.set(false);
    const startMs = performance.now();

    this.endpointService.getEndpoints().subscribe({
      next: (data) => {
        this.apiLastLatencyMs.set(Math.round(performance.now() - startMs));
        this.endpoints.set(data);
        this.lastSyncedAt.set(new Date());
        this.liveJsonOutput.set(JSON.stringify(data, null, 2));
        this.isTestingPublicApi.set(false);
        this.apiPulseActive.set(true);
        setTimeout(() => this.apiPulseActive.set(false), 900);
      },
      error: (err) => {
        const msg = this.endpointService.extractErrorMessages(err);
        this.liveJsonOutput.set(JSON.stringify({ status: 400, messages: msg }, null, 2));
        this.isTestingPublicApi.set(false);
      }
    });
  }

  /*
   * Funciones auxiliares para la vista: arman los ejemplos de cURL, traducen los estados
   * al espanol, generan las barras de disponibilidad y formatean las fechas.
   */
  getCurlSnippet(method: ApiMethodTab): string {
    const url = this.endpointService.publicApiUrl;
    if (method === 'GET') {
      return `curl -X GET "${url}" \\\n  -H "Accept: application/json"`;
    }
    if (method === 'POST') {
      return `curl -X POST "${url}" \\\n  -H "Content-Type: application/json" \\\n  -d '{"name": "Google Search", "url": "https://www.google.com"}'`;
    }
    return `curl -X DELETE "${url}/2"`;
  }

  copyText(text: string, label = 'Copiado al portapapeles'): void {
    navigator.clipboard?.writeText(text);
    this.showToast('success', label);
  }

  private showToast(type: 'success' | 'error', message: string): void {
    this.toast.set({ type, message });
    if (this.toastTimeoutId) clearTimeout(this.toastTimeoutId);
    this.toastTimeoutId = setTimeout(() => {
      this.toast.set(null);
    }, 3200);
  }

  getStatusLabel(status: ServiceStatus): string {
    const labels: Record<ServiceStatus, string> = {
      HEALTHY: 'Operativo',
      DEGRADED: 'Degradado',
      DOWN: 'Caído',
      RECOVERING: 'Recuperando',
      PENDING: 'En Espera (60s)'
    };
    return labels[status];
  }

  getStatusClass(status: ServiceStatus): string {
    return `status-indicator status-${status.toLowerCase()}`;
  }

  getStatusDescription(endpoint: EndpointResponse): string {
    switch (endpoint.status) {
      case 'HEALTHY':
        return 'Respondiendo HTTP 2xx de forma estable dentro de los umbrales de tiempo.';
      case 'DEGRADED':
        return `Inestabilidad detectada (${endpoint.consecutiveFailures}/3 fallos). El motor amortigua el micro-corte antes de declarar incidente.`;
      case 'DOWN':
        return `Incidente activo en curso (${endpoint.consecutiveFailures} fallos seguidos). Computando segundos de caída en el SLA.`;
      case 'RECOVERING':
        return 'Primer ping exitoso tras una caída (1/2). Requiere una segunda confirmación para cerrar el incidente.';
      case 'PENDING':
        return 'Registrado en la API pública. Aguardando el próximo pulso automático del scheduler (cada 60s).';
    }
  }

  getUptimeSegments(endpoint: EndpointResponse): string[] {
    const count = 24;
    if (endpoint.status === 'PENDING') {
      return Array(count).fill('bar-pending');
    }
    const healthyBars = Math.round((endpoint.uptimePercentage / 100) * count);
    const list: string[] = [];

    for (let i = 0; i < count; i++) {
      if (i === count - 1) {
        if (endpoint.status === 'DOWN') {
          list.push('bar-down');
          continue;
        }
        if (endpoint.status === 'DEGRADED') {
          list.push('bar-degraded');
          continue;
        }
        if (endpoint.status === 'RECOVERING') {
          list.push('bar-recovering');
          continue;
        }
      }
      list.push(i < healthyBars ? 'bar-healthy' : 'bar-down');
    }
    return list;
  }

  getWavePath(endpoint: EndpointResponse, index: number): string {
    if (endpoint.status === 'DOWN') {
      return 'M 0 28 Q 45 26, 90 28 T 180 46 L 220 48 L 280 48';
    }
    if (endpoint.status === 'DEGRADED') {
      return 'M 0 32 Q 35 16, 70 30 T 140 24 T 210 42 T 280 36';
    }
    if (index % 2 === 0) {
      return 'M 0 34 C 45 32, 65 16, 110 24 C 155 32, 185 12, 230 20 C 255 24, 268 18, 280 16';
    }
    return 'M 0 30 C 40 18, 80 34, 125 22 C 170 10, 205 28, 245 18 C 265 14, 272 16, 280 14';
  }

  formatRelativeTime(isoDate: string | null): string {
    this.nowTick();
    if (!isoDate) return 'Ciclo en curso';

    const parsed = new Date(isoDate).getTime();
    if (Number.isNaN(parsed)) return 'Ciclo en curso';

    const diffSeconds = Math.max(0, Math.floor((Date.now() - parsed) / 1000));
    if (diffSeconds < 8) return 'hace instantes';
    if (diffSeconds < 60) return `hace ${diffSeconds} s`;

    const diffMinutes = Math.floor(diffSeconds / 60);
    if (diffMinutes < 60) return `hace ${diffMinutes} min`;

    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) return `hace ${diffHours} h`;

    return `hace ${Math.floor(diffHours / 24)} d`;
  }

  formatExactDate(isoDate: string | null): string {
    if (!isoDate) return 'Pendiente de primera ejecución';
    const d = new Date(isoDate);
    if (Number.isNaN(d.getTime())) return isoDate;
    return d.toLocaleString('es-AR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  }
}
