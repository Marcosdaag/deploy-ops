import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EndpointService } from './endpoint.service';
import {
  EndpointResponse,
  IncidentResponse,
  MonthlyReport,
  ServiceMetricsHistory,
  StatusPageData
} from '../models/endpoint.model';

describe('EndpointService', () => {
  let service: EndpointService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [EndpointService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(EndpointService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('deberia consultar la lista de endpoints mediante GET', () => {
    const mockData: EndpointResponse[] = [
      {
        id: 1,
        name: 'Google Search',
        url: 'https://www.google.com',
        status: 'HEALTHY',
        consecutiveFailures: 0,
        lastResponseTimeMs: 72,
        lastCheckedAt: '2026-10-26T20:00:00',
        createdAt: '2026-10-26T19:00:00',
        uptimePercentage: 100.0,
        isProtected: true
      }
    ];

    service.getEndpoints().subscribe((res) => {
      expect(res.length).toBe(1);
      expect(res[0].name).toBe('Google Search');
      expect(res[0].isProtected).toBeTrue();
    });

    const req = httpMock.expectOne(service.publicApiUrl);
    expect(req.request.method).toBe('GET');
    req.flush(mockData);
  });

  it('deberia consultar el historial de incidentes de un servicio mediante GET', () => {
    const mockIncidents: IncidentResponse[] = [
      {
        id: 10,
        serviceId: 1,
        status: 'RESOLVED',
        startedAt: '2026-11-01T10:00:00',
        resolvedAt: '2026-11-01T10:05:00',
        durationSeconds: 300
      }
    ];

    service.getIncidents(1).subscribe((res) => {
      expect(res.length).toBe(1);
      expect(res[0].status).toBe('RESOLVED');
      expect(res[0].durationSeconds).toBe(300);
    });

    const req = httpMock.expectOne(`${service.publicApiUrl}/1/incidents`);
    expect(req.request.method).toBe('GET');
    req.flush(mockIncidents);
  });

  it('deberia consultar metricas historicas mediante GET a /{id}/metrics?range=24h', () => {
    const mockMetrics: ServiceMetricsHistory = {
      serviceId: 1,
      serviceName: 'Google Search',
      range: '24h',
      minLatencyMs: 30,
      maxLatencyMs: 90,
      avgLatencyMs: 50.0,
      p95LatencyMs: 80,
      uptimePercentage: 100.0,
      totalPings: 10,
      points: []
    };

    service.getMetricsHistory(1, '24h').subscribe((res) => {
      expect(res.minLatencyMs).toBe(30);
      expect(res.p95LatencyMs).toBe(80);
    });

    const req = httpMock.expectOne(`${service.publicApiUrl}/1/metrics?range=24h`);
    expect(req.request.method).toBe('GET');
    req.flush(mockMetrics);
  });

  it('deberia alternar la visibilidad publica mediante PATCH a /{id}/visibility', () => {
    service.toggleVisibility(1, true).subscribe();

    const req = httpMock.expectOne(`${service.publicApiUrl}/1/visibility`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ isPublic: true });
    req.flush(null);
  });

  it('deberia obtener los datos de la pagina de estado publica mediante GET a /api/status/{slug}', () => {
    const mockStatus: StatusPageData = {
      slug: 'marcos-dev',
      ownerName: 'Marcos Dev',
      overallStatus: 'OPERATIONAL',
      overallUptime: 100.0,
      services: [],
      recentIncidents: []
    };

    service.getStatusPage('marcos-dev').subscribe((res) => {
      expect(res.slug).toBe('marcos-dev');
      expect(res.ownerName).toBe('Marcos Dev');
    });

    const baseUrl = service.publicApiUrl.replace(/\/endpoints$/, '/status');
    const req = httpMock.expectOne(`${baseUrl}/marcos-dev`);
    expect(req.request.method).toBe('GET');
    req.flush(mockStatus);
  });

  it('deberia disparar un ping inmediato mediante POST', () => {
    const mockResponse: EndpointResponse = {
      id: 1,
      name: 'Google Search',
      url: 'https://www.google.com',
      status: 'HEALTHY',
      consecutiveFailures: 0,
      lastResponseTimeMs: 55,
      lastCheckedAt: '2026-11-03T16:00:00',
      createdAt: '2026-10-26T19:00:00',
      uptimePercentage: 100.0
    };

    service.triggerPing(1).subscribe((res) => {
      expect(res.id).toBe(1);
      expect(res.lastResponseTimeMs).toBe(55);
    });

    const req = httpMock.expectOne(`${service.publicApiUrl}/1/ping`);
    expect(req.request.method).toBe('POST');
    req.flush(mockResponse);
  });

  it('deberia disparar una alerta de prueba mediante POST a /{id}/test-alert', () => {
    service.triggerTestAlert(1).subscribe();

    const req = httpMock.expectOne(`${service.publicApiUrl}/1/test-alert`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('deberia consultar el reporte mensual SLA mediante GET a /{id}/report', () => {
    const mockReport: MonthlyReport = {
      serviceId: 1,
      serviceName: 'Google Search',
      serviceUrl: 'https://www.google.com',
      year: 2026,
      month: 11,
      periodLabel: 'Noviembre 2026',
      uptimePercentage: 99.95,
      totalMonitoredSeconds: 2592000,
      totalDowntimeSeconds: 120,
      totalIncidents: 1,
      mttrMinutes: 2.0,
      minLatencyMs: 30,
      maxLatencyMs: 120,
      avgLatencyMs: 45.0,
      p95LatencyMs: 80,
      incidents: []
    };

    service.getMonthlyReport(1, 2026, 11).subscribe((res) => {
      expect(res.serviceId).toBe(1);
      expect(res.uptimePercentage).toBe(99.95);
      expect(res.periodLabel).toBe('Noviembre 2026');
    });

    const req = httpMock.expectOne(`${service.publicApiUrl}/1/report?year=2026&month=11`);
    expect(req.request.method).toBe('GET');
    req.flush(mockReport);
  });

  it('deberia descargar el reporte mensual CSV mediante GET a /{id}/report/csv', () => {
    const mockCsv = new Blob(['# REPORTE DE CUMPLIMIENTO'], { type: 'text/csv' });

    service.downloadMonthlyReportCsv(1, 2026, 11).subscribe((res) => {
      expect(res).toBeTruthy();
    });

    const req = httpMock.expectOne(`${service.publicApiUrl}/1/report/csv?year=2026&month=11`);
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(mockCsv);
  });

  it('deberia enviar el reporte mensual por email mediante POST a /{id}/report/email', () => {
    service.sendMonthlyReportEmail(1, 2026, 11).subscribe();

    const req = httpMock.expectOne(`${service.publicApiUrl}/1/report/email?year=2026&month=11`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('deberia extraer los mensajes de error devueltos por GlobalExceptionHandler (ej. bloqueo SSRF)', () => {
    const errorResponse = new HttpErrorResponse({
      status: 400,
      statusText: 'Bad Request',
      error: {
        status: 400,
        error: 'Bad Request',
        messages: ['Por seguridad no se permite monitorear localhost ni redes internas.'],
        timestamp: '2026-10-26T20:00:00'
      }
    });

    const messages = service.extractErrorMessages(errorResponse);
    expect(messages).toEqual([
      'Por seguridad no se permite monitorear localhost ni redes internas.'
    ]);
  });
});
