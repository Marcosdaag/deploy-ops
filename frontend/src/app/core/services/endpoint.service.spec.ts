import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EndpointService } from './endpoint.service';
import { EndpointResponse } from '../models/endpoint.model';

/*
 * Pruebas unitarias del servicio HTTP: verificamos que arme bien las peticiones
 * hacia la API publica y que extraiga correctamente los errores del backend.
 */
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
        uptimePercentage: 100.0
      }
    ];

    service.getEndpoints().subscribe((res) => {
      expect(res.length).toBe(1);
      expect(res[0].name).toBe('Google Search');
    });

    const req = httpMock.expectOne(service.publicApiUrl);
    expect(req.request.method).toBe('GET');
    req.flush(mockData);
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
