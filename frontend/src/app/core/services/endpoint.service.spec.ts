import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EndpointService } from './endpoint.service';
import { EndpointResponse, IncidentResponse } from '../models/endpoint.model';

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
