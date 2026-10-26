import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DashboardComponent } from './dashboard.component';
import { EndpointResponse } from '../../core/models/endpoint.model';

/*
 * Pruebas unitarias de la pantalla principal: validamos el calculo reactivo
 * de las metricas globales (SLA, latencia promedio) y los filtros de estado.
 */
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
      uptimePercentage: 100.0
    },
    {
      id: 2,
      name: 'Servicio con Alerta',
      url: 'https://api.ejemplo.com',
      status: 'DEGRADED',
      consecutiveFailures: 1,
      lastResponseTimeMs: 140,
      lastCheckedAt: '2026-10-26T20:00:00',
      createdAt: '2026-10-26T19:00:00',
      uptimePercentage: 98.0
    }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('deberia calcular las metricas globales y filtrar por estado cuando recibe datos del backend', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(component.endpointService.publicApiUrl);
    req.flush(mockEndpoints);

    expect(component.metrics().total).toBe(2);
    expect(component.metrics().healthy).toBe(1);
    expect(component.metrics().degraded).toBe(1);
    expect(component.metrics().globalUptime).toBe(99.0);
    expect(component.metrics().averageLatencyMs).toBe(100);

    component.statusFilter.set('HEALTHY');
    expect(component.filteredEndpoints().length).toBe(1);
    expect(component.filteredEndpoints()[0].name).toBe('Google Search');
  });

  it('deberia validar campos vacios antes de enviar un alta al servidor', () => {
    component.newServiceName.set('');
    component.newServiceUrl.set('');
    component.submitCreateEndpoint();

    expect(component.createErrors().length).toBe(2);
  });
});
