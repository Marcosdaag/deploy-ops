import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { StatusPageComponent } from './status-page.component';
import { StatusPageData } from '../../core/models/endpoint.model';

describe('StatusPageComponent', () => {
  let component: StatusPageComponent;
  let fixture: ComponentFixture<StatusPageComponent>;
  let httpMock: HttpTestingController;

  const mockStatusData: StatusPageData = {
    slug: 'marcos-dev',
    ownerName: 'Marcos Dev',
    overallStatus: 'OPERATIONAL',
    overallUptime: 99.95,
    services: [
      {
        id: 1,
        name: 'API Core',
        urlMasked: 'api.example.com',
        status: 'HEALTHY',
        lastResponseTimeMs: 45,
        lastCheckedAt: '2026-11-03T18:00:00',
        uptimePercentage: 100.0
      }
    ],
    recentIncidents: [
      {
        id: 10,
        serviceName: 'API Core',
        startedAt: '2026-11-03T16:00:00',
        resolvedAt: '2026-11-03T16:15:00',
        status: 'RESOLVED',
        durationSeconds: 900
      }
    ]
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatusPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of({
              get: (key: string) => (key === 'slug' ? 'marcos-dev' : null)
            })
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(StatusPageComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('deberia cargar los datos de la pagina de estado publica desde el backend', () => {
    fixture.detectChanges();

    const baseUrl = component['endpointService'].publicApiUrl.replace(/\/endpoints$/, '/status');
    const req = httpMock.expectOne(`${baseUrl}/marcos-dev`);
    expect(req.request.method).toBe('GET');
    req.flush(mockStatusData);

    expect(component.data()?.ownerName).toBe('Marcos Dev');
    expect(component.data()?.overallStatus).toBe('OPERATIONAL');
    expect(component.data()?.services.length).toBe(1);
    expect(component.data()?.services[0].urlMasked).toBe('api.example.com');
    expect(component.isLoading()).toBeFalse();
  });

  it('deberia formatear correctamente las duraciones de incidentes', () => {
    expect(component.formatDuration(null)).toBe('Menos de 1 min');
    expect(component.formatDuration(30)).toBe('Menos de 1 min');
    expect(component.formatDuration(300)).toBe('5 min');
    expect(component.formatDuration(3660)).toBe('1h 1m');
  });
});
