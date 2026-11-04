import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { DashboardComponent } from './dashboard.component';
import { EndpointResponse, ServiceMetricsHistory } from '../../core/models/endpoint.model';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let httpMock: HttpTestingController;

  const mockEndpoints: EndpointResponse[] = [
    {
      id: 1,
      name: 'Google Search',
      url: 'https://www.google.com',
      status: 'HEALTHY',
      consecutiveFailures: 0,
      lastResponseTimeMs: 60,
      lastCheckedAt: '2026-10-26T20:00:00',
      createdAt: '2026-10-26T19:00:00',
      uptimePercentage: 100.0,
      isProtected: true,
      isOwner: false,
      isPublicStatus: false
    },
    {
      id: 2,
      name: 'Mi API Privada',
      url: 'https://api.ejemplo.com',
      status: 'DEGRADED',
      consecutiveFailures: 1,
      lastResponseTimeMs: 140,
      lastCheckedAt: '2026-10-26T20:00:00',
      createdAt: '2026-10-26T19:00:00',
      uptimePercentage: 98.0,
      isProtected: false,
      isOwner: true,
      isPublicStatus: true
    }
  ];

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('deberia calcular las metricas globales y filtrar por estado cuando recibe datos del backend', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url.startsWith(component.endpointService.publicApiUrl));
    req.flush(mockEndpoints);

    expect(component.metrics().total).toBe(2);
    expect(component.metrics().healthy).toBe(1);
    expect(component.metrics().degraded).toBe(1);
    expect(component.metrics().globalUptime).toBe(99.0);
    expect(component.metrics().averageLatencyMs).toBe(100);

    component.statusFilter.set('HEALTHY');
    expect(component.filteredEndpoints().length).toBe(1);
    expect(component.filteredEndpoints()[0].name).toBe('Google Search');

    component.statusFilter.set('MINE');
    expect(component.filteredEndpoints().length).toBe(1);
    expect(component.filteredEndpoints()[0].name).toBe('Mi API Privada');
  });

  it('deberia abrir el modal de autenticacion si un usuario no logueado intenta crear un endpoint', () => {
    component.newServiceName.set('Mi Endpoint');
    component.newServiceUrl.set('https://miendpoint.com');

    component.submitCreateEndpoint();

    expect(component.isAuthModalOpen()).toBeTrue();
    expect(component.authModalTab()).toBe('LOGIN');
  });

  it('deberia validar campos vacios si el usuario autenticado intenta crear un endpoint sin datos', () => {
    component.authService.currentUser.set({
      id: 1,
      name: 'Marcos',
      email: 'marcos@test.com',
      role: 'ROLE_USER',
      provider: 'LOCAL'
    });

    component.newServiceName.set('');
    component.newServiceUrl.set('');
    component.submitCreateEndpoint();

    expect(component.createErrors().length).toBe(2);
  });

  it('deberia alternar pestañas del modal de autenticacion y resetear errores', () => {
    component.openAuthModal('LOGIN');
    expect(component.isAuthModalOpen()).toBeTrue();
    expect(component.authModalTab()).toBe('LOGIN');

    component.switchAuthTab('REGISTER');
    expect(component.authModalTab()).toBe('REGISTER');

    component.closeAuthModal();
    expect(component.isAuthModalOpen()).toBeFalse();
  });

  it('deberia disparar una alerta de prueba y mostrar toast de exito', () => {
    component.authService.currentUser.set({
      id: 1,
      name: 'Marcos',
      email: 'marcos@test.com',
      role: 'ROLE_USER',
      provider: 'LOCAL'
    });

    component.triggerTestAlert(mockEndpoints[1]);
    expect(component.isTestingAlert()).toBeTrue();

    const req = httpMock.expectOne(`${component.endpointService.publicApiUrl}/2/test-alert`);
    expect(req.request.method).toBe('POST');
    req.flush(null);

    expect(component.isTestingAlert()).toBeFalse();
    expect(component.toast()?.type).toBe('success');
  });

  it('deberia alternar la visibilidad publica de un servicio y actualizar el estado', () => {
    component.toggleServiceVisibility(mockEndpoints[1]);
    expect(component.isTogglingVisibility()).toBeTrue();

    const req = httpMock.expectOne(`${component.endpointService.publicApiUrl}/2/visibility`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ isPublic: false });
    req.flush(null);

    expect(component.isTogglingVisibility()).toBeFalse();
    expect(component.toast()?.type).toBe('success');
  });

  it('deberia consultar metricas historicas al abrir el inspector y construir la grafica SVG', () => {
    component.openInspector(mockEndpoints[0]);
    expect(component.selectedEndpoint()?.id).toBe(1);

    const incReq = httpMock.expectOne(`${component.endpointService.publicApiUrl}/1/incidents`);
    incReq.flush([]);

    const metReq = httpMock.expectOne(`${component.endpointService.publicApiUrl}/1/metrics?range=24h`);
    const mockMetrics: ServiceMetricsHistory = {
      serviceId: 1,
      serviceName: 'Google Search',
      range: '24h',
      minLatencyMs: 40,
      maxLatencyMs: 120,
      avgLatencyMs: 75.5,
      p95LatencyMs: 110,
      uptimePercentage: 100.0,
      totalPings: 2,
      points: [
        { timestampMs: 1700000000000, latencyMs: 50, statusCode: 200, alive: true },
        { timestampMs: 1700000060000, latencyMs: 80, statusCode: 200, alive: true }
      ]
    };
    metReq.flush(mockMetrics);

    const reportReq = httpMock.expectOne((req) => req.url.includes('/1/report'));
    reportReq.flush({
      serviceId: 1,
      serviceName: 'Google Search',
      year: 2026,
      month: 11,
      periodLabel: 'Noviembre 2026',
      uptimePercentage: 100.0,
      totalMonitoredSeconds: 3600,
      totalDowntimeSeconds: 0,
      totalIncidents: 0,
      mttrMinutes: 0.0,
      minLatencyMs: 40,
      maxLatencyMs: 120,
      avgLatencyMs: 75.5,
      p95LatencyMs: 110,
      incidents: []
    });

    expect(component.metricsHistory()?.serviceName).toBe('Google Search');
    expect(component.chartSvg().points.length).toBe(2);
    expect(component.chartSvg().linePath.length).toBeGreaterThan(0);
    expect(component.selectedReport()?.uptimePercentage).toBe(100.0);
  });

  it('deberia navegar entre meses de reporte y recargar metricas de SLA', () => {
    component.selectedEndpoint.set(mockEndpoints[0]);
    component.reportYear.set(2026);
    component.reportMonth.set(11);

    component.changeReportMonth(-1);
    expect(component.reportMonth()).toBe(10);

    const req = httpMock.expectOne(`${component.endpointService.publicApiUrl}/1/report?year=2026&month=10`);
    expect(req.request.method).toBe('GET');
    req.flush({
      serviceId: 1,
      serviceName: 'Google Search',
      year: 2026,
      month: 10,
      periodLabel: 'Octubre 2026',
      uptimePercentage: 99.8,
      totalMonitoredSeconds: 2592000,
      totalDowntimeSeconds: 300,
      totalIncidents: 1,
      mttrMinutes: 5.0,
      minLatencyMs: 30,
      maxLatencyMs: 100,
      avgLatencyMs: 50.0,
      p95LatencyMs: 80,
      incidents: []
    });

    expect(component.selectedReport()?.periodLabel).toBe('Octubre 2026');
  });

  it('deberia enviar el reporte ejecutivo por correo y mostrar toast', () => {
    component.authService.currentUser.set({
      id: 1,
      name: 'Marcos',
      email: 'marcos@test.com',
      role: 'ROLE_USER',
      provider: 'LOCAL'
    });
    component.reportYear.set(2026);
    component.reportMonth.set(11);

    component.sendReportByEmail(mockEndpoints[1]);
    expect(component.isSendingReportEmail()).toBeTrue();

    const req = httpMock.expectOne(`${component.endpointService.publicApiUrl}/2/report/email?year=2026&month=11`);
    expect(req.request.method).toBe('POST');
    req.flush(null);

    expect(component.isSendingReportEmail()).toBeFalse();
    expect(component.toast()?.type).toBe('success');
  });

  it('deberia abrir y cerrar el modal de perfil de usuario', () => {
    component.authService.currentUser.set({
      id: 1,
      name: 'Marcos',
      email: 'marcos@test.com',
      role: 'ROLE_USER',
      provider: 'LOCAL',
      publicSlug: 'marcos-dev'
    });

    component.openProfileModal();
    expect(component.isProfileModalOpen()).toBeTrue();
    expect(component.profileSlugInput()).toBe('marcos-dev');

    component.closeProfileModal();
    expect(component.isProfileModalOpen()).toBeFalse();
  });

  it('deberia actualizar el slug de la status page desde el perfil', () => {
    component.authService.currentUser.set({
      id: 1,
      name: 'Marcos',
      email: 'marcos@test.com',
      role: 'ROLE_USER',
      provider: 'LOCAL',
      publicSlug: 'marcos-dev'
    });

    component.openProfileModal();
    component.profileSlugInput.set('marcos-prod');
    component.updateProfileSlug();

    expect(component.isUpdatingSlug()).toBeTrue();

    const req = httpMock.expectOne((r) => r.url.includes('/api/status/slug'));
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ slug: 'marcos-prod' });
    req.flush({ slug: 'marcos-prod' });

    expect(component.isUpdatingSlug()).toBeFalse();
    expect(component.authService.currentUser()?.publicSlug).toBe('marcos-prod');
    expect(component.profileSlugSuccess()).toContain('marcos-prod');
  });
});
