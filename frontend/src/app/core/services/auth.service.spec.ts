import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { AuthResponse } from '../models/auth.model';

describe('AuthService', () => {
  let service: AuthService;
  let httpTesting: HttpTestingController;

  const mockResponse: AuthResponse = {
    token: 'jwt-test-token-xyz',
    id: 1,
    name: 'Marcos Dev',
    email: 'marcos@test.com',
    role: 'ROLE_USER',
    provider: 'LOCAL'
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  it('debe registrar un usuario, guardar el token en localStorage y actualizar currentUser', () => {
    service.register({ name: 'Marcos Dev', email: 'marcos@test.com', password: 'password123' }).subscribe((res) => {
      expect(res.token).toBe('jwt-test-token-xyz');
      expect(service.currentUser()?.email).toBe('marcos@test.com');
      expect(service.isAuthenticated()).toBeTrue();
      expect(localStorage.getItem('deployops_auth_token')).toBe('jwt-test-token-xyz');
    });

    const req = httpTesting.expectOne(`${service.authBaseUrl}/register`);
    expect(req.request.method).toBe('POST');
    req.flush(mockResponse);
  });

  it('debe iniciar sesion y almacenar la sesion activa', () => {
    service.login({ email: 'marcos@test.com', password: 'password123' }).subscribe((res) => {
      expect(res.name).toBe('Marcos Dev');
      expect(service.isAuthenticated()).toBeTrue();
    });

    const req = httpTesting.expectOne(`${service.authBaseUrl}/login`);
    expect(req.request.method).toBe('POST');
    req.flush(mockResponse);
  });

  it('debe permitir iniciar sesion en modo demo con backend', () => {
    const demoResponse: AuthResponse = {
      token: 'demo-jwt-backend',
      id: 99,
      name: 'Usuario Demo (SRE)',
      email: 'demo@deployops.io',
      role: 'ROLE_USER',
      provider: 'LOCAL',
      publicSlug: 'demo-user'
    };

    service.loginAsDemo().subscribe((res) => {
      expect(res.email).toBe('demo@deployops.io');
      expect(service.isAuthenticated()).toBeTrue();
      expect(service.isDemoSession()).toBeTrue();
    });

    const req = httpTesting.expectOne(`${service.authBaseUrl}/demo`);
    expect(req.request.method).toBe('POST');
    req.flush(demoResponse);
  });

  it('debe iniciar sesion en modo demo con fallback local si el backend no responde', () => {
    service.loginAsDemo().subscribe((res) => {
      expect(res.email).toBe('demo@deployops.io');
      expect(service.currentUser()?.name).toBe('Usuario Demo (SRE)');
      expect(service.isAuthenticated()).toBeTrue();
      expect(service.isDemoSession()).toBeTrue();
    });

    const req = httpTesting.expectOne(`${service.authBaseUrl}/demo`);
    req.error(new ProgressEvent('Network error'));
  });

  it('debe cerrar sesion limpiando localStorage y reseteando currentUser', () => {
    localStorage.setItem('deployops_auth_token', 'token');
    service.logout();

    expect(service.getToken()).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(service.isAuthenticated()).toBeFalse();
  });
});
