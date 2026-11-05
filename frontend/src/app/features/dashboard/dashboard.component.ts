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
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subscription, forkJoin } from 'rxjs';
import {
  DashboardMetrics,
  EndpointCreateRequest,
  EndpointResponse,
  IncidentResponse,
  MonthlyReport,
  ServiceMetricsHistory,
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
import { AuthService } from '../../core/services/auth.service';
import { TelemetryCanvas3dComponent } from './components/telemetry-canvas-3d/telemetry-canvas-3d.component';

type StatusFilter = 'ALL' | 'HEALTHY' | 'ALERT' | 'PENDING' | 'MINE';
type ThemeMode = 'dark' | 'light';
type ApiMethodTab = 'GET' | 'POST' | 'DELETE';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TelemetryCanvas3dComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit, OnDestroy {
  readonly endpointService = inject(EndpointService);
  readonly authService = inject(AuthService);

  readonly isAuthModalOpen = signal<boolean>(false);
  readonly authModalTab = signal<'LOGIN' | 'REGISTER'>('LOGIN');
  readonly authName = signal<string>('');
  readonly authEmail = signal<string>('');
  readonly authPassword = signal<string>('');
  readonly isAuthSubmitting = signal<boolean>(false);
  readonly authErrors = signal<string[]>([]);

  readonly isProfileModalOpen = signal<boolean>(false);
  readonly profileSlugInput = signal<string>('');
  readonly isUpdatingSlug = signal<boolean>(false);
  readonly profileSlugSuccess = signal<string | null>(null);
  readonly profileSlugError = signal<string | null>(null);

  readonly endpoints = signal<EndpointResponse[]>(INITIAL_SHOWCASE_ENDPOINTS);
  readonly isSyncing = signal<boolean>(false);
  readonly lastSyncedAt = signal<Date | null>(null);
  readonly theme = signal<ThemeMode>('dark');

  readonly isScrolled = signal<boolean>(false);
  readonly showScrollTop = signal<boolean>(false);
  readonly cursorX = signal<number>(-500);
  readonly cursorY = signal<number>(-500);

  readonly searchQuery = signal<string>('');
  readonly statusFilter = signal<StatusFilter>('ALL');
  readonly sortBy = signal<'DEFAULT' | 'LATENCY_ASC' | 'LATENCY_DESC' | 'UPTIME_ASC' | 'NAME_ASC'>('DEFAULT');
  readonly activeScope = signal<'mine' | 'showcase'>('mine');
  readonly desktopNotificationsEnabled = signal<boolean>(false);
  private previousDownServiceIds = new Set<number>();

  readonly isEditModalOpen = signal<boolean>(false);
  readonly editingEndpointId = signal<number | null>(null);
  readonly editName = signal<string>('');
  readonly editUrl = signal<string>('');
  readonly editMethod = signal<string>('GET');
  readonly editExpectedStatus = signal<number | null>(null);
  readonly editHeaders = signal<string>('');
  readonly editIsPublic = signal<boolean>(false);
  readonly isSubmittingEdit = signal<boolean>(false);
  readonly editErrors = signal<string[]>([]);

  readonly isCreateModalOpen = signal<boolean>(false);
  readonly newServiceName = signal<string>('');
  readonly newServiceUrl = signal<string>('');
  readonly isSubmittingCreate = signal<boolean>(false);
  readonly createErrors = signal<string[]>([]);
  readonly showAdvancedCreate = signal<boolean>(false);
  readonly newServiceMethod = signal<string>('GET');
  readonly newServiceExpectedStatus = signal<number | null>(null);
  readonly newServiceHeaders = signal<string>('');
  readonly newServiceIsPublic = signal<boolean>(false);

  readonly metricsRange = signal<'6h' | '24h' | '7d'>('24h');
  readonly metricsHistory = signal<ServiceMetricsHistory | null>(null);
  readonly isLoadingMetrics = signal<boolean>(false);
  readonly isTogglingVisibility = signal<boolean>(false);
  readonly activeHoverPoint = signal<{ x: number; y: number; latency: number; time: string; alive: boolean } | null>(null);

  readonly selectedEndpoint = signal<EndpointResponse | null>(null);
  readonly selectedEndpointIncidents = signal<IncidentResponse[]>([]);
  readonly isLoadingIncidents = signal<boolean>(false);
  readonly isTriggeringPing = signal<boolean>(false);
  readonly isTestingAlert = signal<boolean>(false);

  readonly selectedReport = signal<MonthlyReport | null>(null);
  readonly reportYear = signal<number>(new Date().getFullYear());
  readonly reportMonth = signal<number>(new Date().getMonth() + 1);
  readonly isLoadingReport = signal<boolean>(false);
  readonly isSendingReportEmail = signal<boolean>(false);
  readonly isDownloadingReportCsv = signal<boolean>(false);

  readonly endpointToDelete = signal<EndpointResponse | null>(null);
  readonly isDeleting = signal<boolean>(false);

  readonly activePipelineStep = signal<number>(1);
  readonly pipelineSteps = [
    {
      step: 1,
      tag: 'DISPATCH',
      name: 'Loom Virtual Threads',
      runtime: 'Java 21 · Executor Concurrente',
      summary: 'Despacho masivo no bloqueante de hilos virtuales ultra-ligeros con memoria constante por cada endpoint evaluado.',
      metrics: 'Memoria: ~1KB/hilo · Concurrencia O(N)',
      codeSnippet: `Executors.newVirtualThreadPerTaskExecutor()
  .submit(() -> monitorEngine.sweepEndpoint(endpoint));`
    },
    {
      step: 2,
      tag: 'SEGURIDAD',
      name: 'Escudo Anti-SSRF & DNS Guard',
      runtime: 'UrlSecurityValidator (RFC 1918)',
      summary: 'Resolucion e inspeccion previa a la conexion. Bloqueo estricto de loopbacks IPv4/IPv6, rangos privados y metadatos cloud (169.254.169.254).',
      metrics: 'RFC 1918 / 6598 · Puertos 22/3306/5432 filtrados',
      codeSnippet: `InetAddress address = InetAddress.getByName(uri.getHost());
if (isPrivateOrLoopback(address) || SENSITIVE_PORTS.contains(port)) {
    throw new SecurityException("SSRF blocked");
}`
    },
    {
      step: 3,
      tag: 'ESTADO',
      name: 'Maquina de Estados & Anti-Flapping',
      runtime: 'Finite State Machine (4 Estados)',
      summary: 'Amortiguacion de micro-cortes transitorios. Requiere 3 fallos consecutivos para abrir incidentes y 2 exitos seguidos para confirmar recuperacion.',
      metrics: 'Estados: HEALTHY -> DEGRADED -> DOWN -> RECOVERING',
      codeSnippet: `if (failures >= 3 && currentState == DEGRADED) {
    transitionTo(DOWN);
    incidentRepository.open(endpoint);
}`
    },
    {
      step: 4,
      tag: 'PERSISTENCIA',
      name: 'SLA O(1) & PostgreSQL 16',
      runtime: 'Supabase Cloud Managed DB',
      summary: 'Calculo de disponibilidad de 30 dias en base a particionamiento y consultas agregadas sin sobrecarga de consultas N+1.',
      metrics: 'Uptime mensual instantaneo · Indices temporales',
      codeSnippet: `SELECT service_id, COUNT(*) FILTER (WHERE status = 200) * 100.0 / COUNT(*)
FROM ping_logs WHERE created_at >= NOW() - INTERVAL '30 days' GROUP BY service_id;`
    },
    {
      step: 5,
      tag: 'TELEMETRIA',
      name: 'Transmision Reactiva SSE',
      runtime: 'Spring WebFlux -> Angular 20 Signals',
      summary: 'Canal Server-Sent Events permanente que emite la telemetria en tiempo real actualizando los Signals del frontend sin sobrecargar la red.',
      metrics: 'Brotli/Gzip · Reconexion con backoff exponencial',
      codeSnippet: `sseEmitter.send(SseEmitter.event()
  .name("telemetry-pulse")
  .data(serviceSummaryDto));`
    }
  ];

  readonly isSimulatingPipeline = signal<boolean>(false);
  private pipelineSimIntervalId: ReturnType<typeof setInterval> | null = null;

  setPipelineStep(step: number): void {
    this.activePipelineStep.set(step);
    if (this.isSimulatingPipeline() && this.pipelineSimIntervalId) {
      clearInterval(this.pipelineSimIntervalId);
      this.isSimulatingPipeline.set(false);
    }
  }

  nextPipelineStep(): void {
    const curr = this.activePipelineStep();
    this.setPipelineStep(curr < 5 ? curr + 1 : 1);
  }

  prevPipelineStep(): void {
    const curr = this.activePipelineStep();
    this.setPipelineStep(curr > 1 ? curr - 1 : 5);
  }

  simulatePipelineRun(): void {
    if (this.isSimulatingPipeline()) {
      if (this.pipelineSimIntervalId) clearInterval(this.pipelineSimIntervalId);
      this.isSimulatingPipeline.set(false);
      return;
    }

    this.isSimulatingPipeline.set(true);
    this.activePipelineStep.set(1);
    let currentStep = 1;

    this.pipelineSimIntervalId = setInterval(() => {
      currentStep++;
      if (currentStep > 5) {
        if (this.pipelineSimIntervalId) clearInterval(this.pipelineSimIntervalId);
        this.isSimulatingPipeline.set(false);
        this.toast.set({ type: 'success', message: 'Recorrido simulado: Comprobacion evaluada y despachada por SSE exitosamente.' });
      } else {
        this.activePipelineStep.set(currentStep);
      }
    }, 900);
  }

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
  private sseSubscription: Subscription | null = null;
  private hasAttemptedAutoSeed = false;

  readonly featuredPair = computed<EndpointResponse[]>(() => {
    const list = this.endpoints();
    if (list.length >= 2) return list.slice(0, 2);
    if (list.length === 1) return [list[0], INITIAL_SHOWCASE_ENDPOINTS[1]];
    return INITIAL_SHOWCASE_ENDPOINTS;
  });

  readonly chartSvg = computed<{
    linePath: string;
    areaPath: string;
    points: { x: number; y: number; latency: number; time: string; alive: boolean }[];
  }>(() => {
    const history = this.metricsHistory();
    if (!history || !history.points || history.points.length === 0) {
      return { linePath: '', areaPath: '', points: [] };
    }

    const pts = history.points;
    const width = 340;
    const height = 90;
    const paddingX = 12;
    const paddingY = 12;
    const drawWidth = width - paddingX * 2;
    const drawHeight = height - paddingY * 2;

    const latencies = pts.map((p) => p.latencyMs);
    const minVal = Math.min(...latencies);
    const maxVal = Math.max(...latencies, minVal + 1);
    const range = maxVal - minVal;

    const mapped = pts.map((p, idx) => {
      const x = paddingX + (pts.length > 1 ? (idx / (pts.length - 1)) * drawWidth : drawWidth / 2);
      const ratio = (p.latencyMs - minVal) / range;
      const y = paddingY + drawHeight - ratio * drawHeight;
      const timeStr = new Date(p.timestampMs).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      return { x, y, latency: p.latencyMs, time: timeStr, alive: p.alive };
    });

    if (mapped.length === 1) {
      const single = mapped[0];
      return {
        linePath: `M ${single.x - 20} ${single.y} L ${single.x + 20} ${single.y}`,
        areaPath: `M ${single.x - 20} ${height} L ${single.x - 20} ${single.y} L ${single.x + 20} ${single.y} L ${single.x + 20} ${height} Z`,
        points: mapped
      };
    }

    const linePath = mapped.reduce(
      (acc, curr, idx) => acc + (idx === 0 ? 'M ' : ' L ') + curr.x.toFixed(1) + ' ' + curr.y.toFixed(1),
      ''
    );
    const first = mapped[0];
    const last = mapped[mapped.length - 1];
    const areaPath = linePath + ' L ' + last.x.toFixed(1) + ' ' + height + ' L ' + first.x.toFixed(1) + ' ' + height + ' Z';

    return { linePath, areaPath, points: mapped };
  });

  readonly myEndpointsCount = computed<number>(() => {
    return this.endpoints().filter((e) => !!e.isOwner).length;
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
    const sort = this.sortBy();

    let list = this.endpoints().filter((item) => {
      const matchesQuery =
        !query ||
        item.name.toLowerCase().includes(query) ||
        item.url.toLowerCase().includes(query);

      if (!matchesQuery) return false;
      if (filter === 'HEALTHY') return item.status === 'HEALTHY';
      if (filter === 'ALERT') return item.status === 'DOWN' || item.status === 'DEGRADED';
      if (filter === 'PENDING') return item.status === 'PENDING' || item.status === 'RECOVERING';
      if (filter === 'MINE') return !!item.isOwner;
      return true;
    });

    if (sort === 'LATENCY_ASC') {
      list = [...list].sort((a, b) => (a.lastResponseTimeMs ?? 999999) - (b.lastResponseTimeMs ?? 999999));
    } else if (sort === 'LATENCY_DESC') {
      list = [...list].sort((a, b) => (b.lastResponseTimeMs ?? -1) - (a.lastResponseTimeMs ?? -1));
    } else if (sort === 'UPTIME_ASC') {
      list = [...list].sort((a, b) => a.uptimePercentage - b.uptimePercentage);
    } else if (sort === 'NAME_ASC') {
      list = [...list].sort((a, b) => a.name.localeCompare(b.name));
    }

    return list;
  });

  readonly currentStateInfo = computed<StateNodeInfo>(() => {
    const current = this.activeStateNode();
    return this.stateNodes.find((n) => n.status === current) ?? this.stateNodes[1];
  });

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

  scrollToSection(event: Event, sectionId: string): void {
    if (event) {
      event.preventDefault();
    }
    const element = document.getElementById(sectionId);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth' });
      if (typeof history !== 'undefined') {
        history.replaceState(null, '', `${window.location.pathname}#${sectionId}`);
      }
    }
  }

  ngOnInit(): void {
    this.initTheme();
    this.initDesktopNotifications();
    this.onWindowScroll();
    this.syncWithPublicApi();
    this.initSseStream();

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
    if (this.sseSubscription) this.sseSubscription.unsubscribe();
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

  private initSseStream(): void {
    this.sseSubscription = this.endpointService.getDashboardStream().subscribe({
      next: (streamData) => {
        if (this.authService.isAuthenticated()) {
          this.syncWithPublicApi();
        } else {
          if (streamData && streamData.length > 0) {
            this.endpoints.set(streamData);
            this.lastSyncedAt.set(new Date());
            this.liveJsonOutput.set(JSON.stringify(streamData, null, 2));

            const current = this.selectedEndpoint();
            if (current) {
              const updated = streamData.find((item) => item.id === current.id) ?? null;
              this.selectedEndpoint.set(updated);
            }
          }
        }
      }
    });
  }

  switchScope(scope: 'mine' | 'showcase'): void {
    this.activeScope.set(scope);
    this.syncWithPublicApi();
  }

  seedSampleEndpoints(): void {
    if (!this.authService.isAuthenticated()) {
      this.openAuthModal('LOGIN');
      return;
    }
    this.isSyncing.set(true);
    this.endpointService.seedSampleEndpoints().subscribe({
      next: (data) => {
        this.isSyncing.set(false);
        this.endpoints.set(data);
        this.activeScope.set('mine');
        this.showToast('success', 'Endpoints de ejemplo cargados en tu cuenta');
      },
      error: (err) => {
        this.isSyncing.set(false);
        this.showToast('error', this.endpointService.extractErrorMessages(err)[0] || 'Error al cargar ejemplos');
      }
    });
  }

  syncWithPublicApi(): void {
    this.isSyncing.set(true);
    const startMs = performance.now();
    const scope = this.authService.isAuthenticated() ? this.activeScope() : 'showcase';

    this.endpointService.getEndpoints(scope).subscribe({
      next: (data) => {
        if (!this.authService.isAuthenticated() && data.length === 0 && !this.hasAttemptedAutoSeed) {
          this.hasAttemptedAutoSeed = true;
          this.seedDefaultShowcaseEndpoints();
          return;
        }

        this.apiLastLatencyMs.set(Math.round(performance.now() - startMs));
        this.endpoints.set(data);
        this.checkIncidentTransitions(data);
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

  openCreateModal(): void {
    if (!this.authService.isAuthenticated()) {
      this.openAuthModal('LOGIN');
      return;
    }
    this.clearCreateErrors();
    this.isCreateModalOpen.set(true);
  }

  closeCreateModal(): void {
    this.isCreateModalOpen.set(false);
    this.clearCreateErrors();
  }

  submitCreateEndpoint(): void {
    if (!this.authService.isAuthenticated()) {
      this.openAuthModal('LOGIN');
      this.showToast('error', 'Inicia sesión o crea una cuenta para añadir tus propios endpoints');
      return;
    }

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

    const payload: EndpointCreateRequest = {
      name,
      url,
      httpMethod: this.newServiceMethod(),
      expectedStatusCode: this.newServiceExpectedStatus() || undefined,
      customHeaders: this.newServiceHeaders().trim() || undefined,
      isPublicStatus: this.newServiceIsPublic()
    };

    this.endpointService.createEndpoint(payload).subscribe({
      next: (created) => {
        this.activeScope.set('mine');
        this.endpoints.update((prev) => [created, ...prev]);
        this.newServiceName.set('');
        this.newServiceUrl.set('');
        this.newServiceMethod.set('GET');
        this.newServiceExpectedStatus.set(null);
        this.newServiceHeaders.set('');
        this.newServiceIsPublic.set(false);
        this.showAdvancedCreate.set(false);
        this.isSubmittingCreate.set(false);
        this.isCreateModalOpen.set(false);
        this.liveJsonOutput.set(JSON.stringify(this.endpoints(), null, 2));
        this.showToast('success', `${created.name} sumado al monitor`);
      },
      error: (err) => {
        this.createErrors.set(this.endpointService.extractErrorMessages(err));
        this.isSubmittingCreate.set(false);
      }
    });
  }

  openEditModal(endpoint: EndpointResponse, event?: Event): void {
    if (event) event.stopPropagation();
    this.editingEndpointId.set(endpoint.id);
    this.editName.set(endpoint.name);
    this.editUrl.set(endpoint.url);
    this.editMethod.set(endpoint.httpMethod || 'GET');
    this.editExpectedStatus.set(endpoint.expectedStatusCode ?? null);
    this.editHeaders.set(endpoint.customHeaders || '');
    this.editIsPublic.set(!!endpoint.isPublicStatus);
    this.editErrors.set([]);
    this.isEditModalOpen.set(true);
  }

  closeEditModal(): void {
    this.isEditModalOpen.set(false);
    this.editingEndpointId.set(null);
    this.editErrors.set([]);
  }

  submitEditEndpoint(): void {
    const id = this.editingEndpointId();
    if (!id) return;

    const name = this.editName().trim();
    const url = this.editUrl().trim();

    const localErrors: string[] = [];
    if (!name) localErrors.push('Ingresa un nombre para el servicio.');
    if (!url) localErrors.push('Ingresa la URL HTTP o HTTPS.');

    if (localErrors.length > 0) {
      this.editErrors.set(localErrors);
      return;
    }

    this.isSubmittingEdit.set(true);
    this.editErrors.set([]);

    this.endpointService.updateEndpoint(id, {
      name,
      url,
      httpMethod: this.editMethod(),
      expectedStatusCode: this.editExpectedStatus() || null,
      customHeaders: this.editHeaders().trim() || null,
      isPublicStatus: this.editIsPublic()
    }).subscribe({
      next: (updated) => {
        this.isSubmittingEdit.set(false);
        this.endpoints.update((list) =>
          list.map((item) => (item.id === updated.id ? { ...item, ...updated } : item))
        );
        if (this.selectedEndpoint()?.id === updated.id) {
          this.selectedEndpoint.set(updated);
        }
        this.closeEditModal();
        this.showToast('success', `${updated.name} actualizado correctamente`);
      },
      error: (err) => {
        this.isSubmittingEdit.set(false);
        this.editErrors.set(this.endpointService.extractErrorMessages(err));
      }
    });
  }

  initDesktopNotifications(): void {
    if (typeof window !== 'undefined' && 'Notification' in window) {
      const saved = localStorage.getItem('deployops_desktop_alerts') === 'true';
      if (saved && Notification.permission === 'granted') {
        this.desktopNotificationsEnabled.set(true);
      }
    }
  }

  toggleDesktopNotifications(): void {
    if (typeof window === 'undefined' || !('Notification' in window)) {
      this.showToast('error', 'Tu navegador no soporta notificaciones de escritorio');
      return;
    }

    if (this.desktopNotificationsEnabled()) {
      this.desktopNotificationsEnabled.set(false);
      localStorage.setItem('deployops_desktop_alerts', 'false');
      this.showToast('success', 'Alertas de escritorio desactivadas');
      return;
    }

    Notification.requestPermission().then((permission) => {
      if (permission === 'granted') {
        this.desktopNotificationsEnabled.set(true);
        localStorage.setItem('deployops_desktop_alerts', 'true');
        this.showToast('success', 'Alertas de escritorio activadas');
        this.playNotificationTone();
      } else {
        this.desktopNotificationsEnabled.set(false);
        this.showToast('error', 'Permiso de notificaciones denegado en el navegador');
      }
    });
  }

  playNotificationTone(): void {
    try {
      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      if (!AudioCtx) return;
      const ctx = new AudioCtx();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(587.33, ctx.currentTime);
      osc.frequency.setValueAtTime(880, ctx.currentTime + 0.1);
      gain.gain.setValueAtTime(0.12, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.35);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + 0.35);
    } catch {
      // AudioContext restriction
    }
  }

  private checkIncidentTransitions(incoming: EndpointResponse[]): void {
    const currentDownIds = new Set<number>();
    for (const ep of incoming) {
      if (ep.status === 'DOWN') {
        currentDownIds.add(ep.id);
        if (!this.previousDownServiceIds.has(ep.id)) {
          if (this.desktopNotificationsEnabled() && typeof window !== 'undefined' && 'Notification' in window && Notification.permission === 'granted') {
            try {
              new Notification(`🚨 Alerta: ${ep.name} está CAÍDO`, {
                body: `DeployOps detectó fallos consecutivos en ${ep.url}`,
                icon: '/favicon.ico'
              });
            } catch {}
            this.playNotificationTone();
          }
        }
      }
    }
    this.previousDownServiceIds = currentDownIds;
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
    this.loadIncidents(endpoint.id);
    this.loadMetricsHistory(endpoint.id, this.metricsRange());
    this.loadMonthlyReport(endpoint.id);
  }

  loadMonthlyReport(serviceId: number, year = this.reportYear(), month = this.reportMonth()): void {
    this.isLoadingReport.set(true);
    this.endpointService.getMonthlyReport(serviceId, year, month).subscribe({
      next: (report) => {
        this.selectedReport.set(report);
        this.isLoadingReport.set(false);
      },
      error: () => {
        this.selectedReport.set(null);
        this.isLoadingReport.set(false);
      }
    });
  }

  changeReportMonth(monthDiff: number): void {
    let year = this.reportYear();
    let month = this.reportMonth() + monthDiff;
    if (month < 1) {
      month = 12;
      year--;
    } else if (month > 12) {
      month = 1;
      year++;
    }
    this.reportYear.set(year);
    this.reportMonth.set(month);
    const ep = this.selectedEndpoint();
    if (ep) {
      this.loadMonthlyReport(ep.id, year, month);
    }
  }

  downloadReportCsv(endpoint: EndpointResponse): void {
    if (this.isDownloadingReportCsv()) return;
    this.isDownloadingReportCsv.set(true);
    this.endpointService.downloadMonthlyReportCsv(endpoint.id, this.reportYear(), this.reportMonth()).subscribe({
      next: (blob) => {
        this.isDownloadingReportCsv.set(false);
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        const safeName = endpoint.name.toLowerCase().replace(/[^a-z0-9]/g, '-');
        a.download = `reporte-sla-${safeName}-${this.reportYear()}-${String(this.reportMonth()).padStart(2, '0')}.csv`;
        a.click();
        window.URL.revokeObjectURL(url);
        this.showToast('success', 'Reporte CSV descargado exitosamente');
      },
      error: () => {
        this.isDownloadingReportCsv.set(false);
        this.showToast('error', 'No se pudo descargar el reporte CSV');
      }
    });
  }

  sendReportByEmail(endpoint: EndpointResponse): void {
    if (this.isSendingReportEmail()) return;
    this.isSendingReportEmail.set(true);
    this.endpointService.sendMonthlyReportEmail(endpoint.id, this.reportYear(), this.reportMonth()).subscribe({
      next: () => {
        this.isSendingReportEmail.set(false);
        const email = this.authService.currentUser()?.email || 'tu correo registrado';
        this.showToast('success', `Reporte ejecutivo enviado a ${email}`);
      },
      error: (err) => {
        this.isSendingReportEmail.set(false);
        const msg = this.endpointService.extractErrorMessages(err)[0];
        this.showToast('error', msg || 'No se pudo enviar el reporte por correo');
      }
    });
  }

  closeInspector(): void {
    this.selectedEndpoint.set(null);
    this.selectedEndpointIncidents.set([]);
    this.metricsHistory.set(null);
    this.activeHoverPoint.set(null);
  }

  loadMetricsHistory(serviceId: number, range: '6h' | '24h' | '7d' = '24h'): void {
    this.isLoadingMetrics.set(true);
    this.endpointService.getMetricsHistory(serviceId, range).subscribe({
      next: (data) => {
        this.metricsHistory.set(data);
        this.isLoadingMetrics.set(false);
      },
      error: () => {
        this.metricsHistory.set(null);
        this.isLoadingMetrics.set(false);
      }
    });
  }

  changeMetricsRange(range: '6h' | '24h' | '7d'): void {
    this.metricsRange.set(range);
    const selected = this.selectedEndpoint();
    if (selected) {
      this.loadMetricsHistory(selected.id, range);
    }
  }

  toggleServiceVisibility(endpoint: EndpointResponse): void {
    if (this.isTogglingVisibility()) return;
    const newStatus = !endpoint.isPublicStatus;
    this.isTogglingVisibility.set(true);
    this.endpointService.toggleVisibility(endpoint.id, newStatus).subscribe({
      next: () => {
        this.isTogglingVisibility.set(false);
        const updated = { ...endpoint, isPublicStatus: newStatus };
        this.selectedEndpoint.set(updated);
        this.endpoints.update((prev) =>
          prev.map((item) => (item.id === endpoint.id ? updated : item))
        );
        this.showToast(
          'success',
          newStatus ? 'Servicio publicado en tu Status Page' : 'Servicio retirado de la Status Page'
        );
      },
      error: (err) => {
        this.isTogglingVisibility.set(false);
        const msg = this.endpointService.extractErrorMessages(err)[0];
        this.showToast('error', msg);
      }
    });
  }

  copyStatusPageLink(): void {
    const user = this.authService.currentUser();
    const slug = user?.publicSlug || 'demo';
    const url = `${window.location.origin}/status/${slug}`;
    if (navigator.clipboard) {
      navigator.clipboard.writeText(url).then(() => {
        this.showToast('success', 'Enlace de Status Page copiado al portapapeles');
      });
    }
  }

  toggleAdvancedCreate(): void {
    this.showAdvancedCreate.update((v) => !v);
  }

  setExpectedStatus(val: unknown): void {
    const num = Number(val);
    this.newServiceExpectedStatus.set(!isNaN(num) && num > 0 ? num : null);
  }

  loadIncidents(serviceId: number): void {
    this.isLoadingIncidents.set(true);
    this.endpointService.getIncidents(serviceId).subscribe({
      next: (data) => {
        this.selectedEndpointIncidents.set(data);
        this.isLoadingIncidents.set(false);
      },
      error: () => {
        this.selectedEndpointIncidents.set([]);
        this.isLoadingIncidents.set(false);
      }
    });
  }

  triggerTestAlert(endpoint: EndpointResponse): void {
    if (this.isTestingAlert()) return;
    this.isTestingAlert.set(true);
    this.endpointService.triggerTestAlert(endpoint.id).subscribe({
      next: () => {
        this.isTestingAlert.set(false);
        const email = this.authService.currentUser()?.email || 'tu correo';
        this.showToast('success', `Alerta de prueba enviada a ${email}`);
      },
      error: () => {
        this.isTestingAlert.set(false);
        this.showToast('error', 'No se pudo enviar la alerta de prueba');
      }
    });
  }

  triggerManualPing(endpoint: EndpointResponse, event?: Event): void {
    if (event) event.stopPropagation();
    if (this.isTriggeringPing()) return;

    this.isTriggeringPing.set(true);
    this.endpointService.triggerPing(endpoint.id).subscribe({
      next: (updated) => {
        this.endpoints.update((prev) =>
          prev.map((item) => (item.id === updated.id ? updated : item))
        );
        if (this.selectedEndpoint()?.id === updated.id) {
          this.selectedEndpoint.set(updated);
          this.loadIncidents(updated.id);
        }
        this.isTriggeringPing.set(false);
        this.showToast(
          'success',
          `Ping ejecutado en ${updated.name}: ${updated.lastResponseTimeMs ?? 0} ms`
        );
      },
      error: (err) => {
        this.isTriggeringPing.set(false);
        const msg = this.endpointService.extractErrorMessages(err)[0];
        this.showToast('error', msg);
      }
    });
  }

  formatDuration(seconds: number): string {
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    const remSeconds = seconds % 60;
    if (minutes < 60) {
      return remSeconds > 0 ? `${minutes}m ${remSeconds}s` : `${minutes}m`;
    }
    const hours = Math.floor(minutes / 60);
    const remMinutes = minutes % 60;
    return `${hours}h ${remMinutes}m`;
  }

  triggerPublicApiTest(): void {
    this.isTestingPublicApi.set(true);
    this.apiPulseActive.set(false);
    const startMs = performance.now();

    this.endpointService.getEndpoints().subscribe({
      next: (data) => {
        this.apiLastLatencyMs.set(Math.round(performance.now() - startMs));
        this.endpoints.set(data);
        this.checkIncidentTransitions(data);
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

  getCurlSnippet(method: ApiMethodTab): string {
    const url = this.endpointService.publicApiUrl;
    if (method === 'GET') {
      return `curl -X GET "${url}" \
  -H "Accept: application/json"`;
    }
    if (method === 'POST') {
      return `curl -X POST "${url}" \
  -H "Content-Type: application/json" \
  -d '{"name": "Google Search", "url": "https://www.google.com"}'`;
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

  getLatencyClass(latency: number | null): string {
    if (latency === null || latency <= 0) return 'latency-muted';
    if (latency < 200) return 'latency-healthy';
    if (latency < 800) return 'latency-degraded';
    return 'latency-danger';
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

  openAuthModal(tab: 'LOGIN' | 'REGISTER' = 'LOGIN'): void {
    this.authModalTab.set(tab);
    this.authErrors.set([]);
    this.isAuthModalOpen.set(true);
  }

  closeAuthModal(): void {
    if (this.isAuthSubmitting()) return;
    this.isAuthModalOpen.set(false);
    this.authErrors.set([]);
  }

  switchAuthTab(tab: 'LOGIN' | 'REGISTER'): void {
    this.authModalTab.set(tab);
    this.authErrors.set([]);
  }

  clearAuthErrors(): void {
    this.authErrors.set([]);
  }

  submitAuth(): void {
    const email = this.authEmail().trim();
    const password = this.authPassword().trim();
    const tab = this.authModalTab();

    if (!email || !password) {
      this.authErrors.set(['Completa todos los campos obligatorios.']);
      return;
    }

    if (tab === 'REGISTER') {
      const name = this.authName().trim();
      if (!name) {
        this.authErrors.set(['Ingresa tu nombre para el registro.']);
        return;
      }

      this.isAuthSubmitting.set(true);
      this.authService.register({ name, email, password }).subscribe({
        next: (res) => {
          this.isAuthSubmitting.set(false);
          this.isAuthModalOpen.set(false);
          this.resetAuthForm();
          this.syncWithPublicApi();
          this.showToast('success', `¡Bienvenido, ${res.name}!`);
        },
        error: (err) => {
          this.isAuthSubmitting.set(false);
          this.authErrors.set(this.endpointService.extractErrorMessages(err));
        }
      });
    } else {
      this.isAuthSubmitting.set(true);
      this.authService.login({ email, password }).subscribe({
        next: (res) => {
          this.isAuthSubmitting.set(false);
          this.isAuthModalOpen.set(false);
          this.resetAuthForm();
          this.syncWithPublicApi();
          this.showToast('success', `Hola de nuevo, ${res.name}`);
        },
        error: (err) => {
          this.isAuthSubmitting.set(false);
          this.authErrors.set(this.endpointService.extractErrorMessages(err));
        }
      });
    }
  }

  triggerGoogleAuth(): void {
    if (typeof window !== 'undefined' && (window as any).google?.accounts?.id) {
      try {
        (window as any).google.accounts.id.initialize({
          client_id: '1088424263544-demo.apps.googleusercontent.com',
          callback: (res: any) => {
            if (res?.credential) {
              this.handleGoogleCredential(res.credential);
            }
          }
        });
        (window as any).google.accounts.id.prompt();
        return;
      } catch {
        // Fallback
      }
    }

    const demoEmail = this.authEmail().trim() || 'demo.developer@gmail.com';
    const demoName = this.authName().trim() || demoEmail.split('@')[0];
    const payload = JSON.stringify({
      iss: 'https://accounts.google.com',
      email: demoEmail,
      name: demoName,
      sub: 'google-sub-' + Date.now()
    });
    const encodedPayload = btoa(unescape(encodeURIComponent(payload)))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
    const dummyJwt = `eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.${encodedPayload}.signature`;

    this.handleGoogleCredential(dummyJwt);
  }

  handleGoogleCredential(credential: string): void {
    this.isAuthSubmitting.set(true);
    this.authService.loginWithGoogle(credential).subscribe({
      next: (res) => {
        this.isAuthSubmitting.set(false);
        this.isAuthModalOpen.set(false);
        this.resetAuthForm();
        this.syncWithPublicApi();
        this.showToast('success', `Sesión iniciada con Google (${res.email})`);
      },
      error: (err) => {
        this.isAuthSubmitting.set(false);
        this.authErrors.set(this.endpointService.extractErrorMessages(err));
      }
    });
  }

  logout(): void {
    this.authService.logout();
    this.syncWithPublicApi();
    this.showToast('success', 'Sesión cerrada correctamente');
  }

  openProfileModal(): void {
    const user = this.authService.currentUser();
    this.profileSlugInput.set(user?.publicSlug || '');
    this.profileSlugSuccess.set(null);
    this.profileSlugError.set(null);
    this.isProfileModalOpen.set(true);
  }

  closeProfileModal(): void {
    this.isProfileModalOpen.set(false);
  }

  updateProfileSlug(): void {
    const slug = this.profileSlugInput().trim();
    if (slug.length < 3) {
      this.profileSlugError.set('El slug debe tener al menos 3 caracteres.');
      return;
    }
    this.isUpdatingSlug.set(true);
    this.profileSlugError.set(null);
    this.profileSlugSuccess.set(null);

    this.endpointService.updateUserSlug(slug).subscribe({
      next: (res) => {
        this.isUpdatingSlug.set(false);
        const curr = this.authService.currentUser();
        if (curr) {
          const updated = { ...curr, publicSlug: res.slug };
          this.authService.currentUser.set(updated);
          localStorage.setItem('deployops_auth_user', JSON.stringify(updated));
        }
        this.profileSlugSuccess.set(`Slug actualizado a "${res.slug}"`);
        this.showToast('success', 'URL pública actualizada');
      },
      error: (err) => {
        this.isUpdatingSlug.set(false);
        const msg = this.endpointService.extractErrorMessages(err)[0];
        this.profileSlugError.set(msg || 'Error al actualizar el slug');
      }
    });
  }

  private resetAuthForm(): void {
    this.authName.set('');
    this.authEmail.set('');
    this.authPassword.set('');
    this.authErrors.set([]);
  }
}
