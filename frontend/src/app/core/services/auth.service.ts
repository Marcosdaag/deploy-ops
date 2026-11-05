import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthResponse,
  AuthUser,
  GoogleLoginRequest,
  LoginRequest,
  RegisterRequest
} from '../models/auth.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly TOKEN_KEY = 'deployops_auth_token';
  private readonly USER_KEY = 'deployops_auth_user';

  readonly authBaseUrl = environment.apiUrl.replace(/\/endpoints\/?$/, '/auth');

  readonly currentUser = signal<AuthUser | null>(this.loadStoredUser());
  readonly isAuthenticated = computed(() => !!this.currentUser());
  readonly isDemoSession = computed(() => {
    const user = this.currentUser();
    return user?.provider === 'DEMO' || user?.email === 'demo@deployops.io';
  });

  register(payload: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.authBaseUrl}/register`, payload).pipe(
      tap((res) => this.persistSession(res))
    );
  }

  login(payload: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.authBaseUrl}/login`, payload).pipe(
      tap((res) => this.persistSession(res))
    );
  }

  loginWithGoogle(credential: string): Observable<AuthResponse> {
    const payload: GoogleLoginRequest = { credential };
    return this.http.post<AuthResponse>(`${this.authBaseUrl}/google`, payload).pipe(
      tap((res) => this.persistSession(res))
    );
  }

  loginAsDemo(): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.authBaseUrl}/demo`, {}).pipe(
      tap((res) => this.persistSession(res)),
      catchError(() => {
        const fallbackDemo: AuthResponse = {
          token: 'deployops_demo_token_session',
          id: 99999,
          name: 'Usuario Demo (SRE)',
          email: 'demo@deployops.io',
          role: 'ROLE_USER',
          provider: 'DEMO',
          publicSlug: 'demo-user'
        };
        this.persistSession(fallbackDemo);
        return of(fallbackDemo);
      })
    );
  }

  logout(): void {
    if (typeof window !== 'undefined') {
      localStorage.removeItem(this.TOKEN_KEY);
      localStorage.removeItem(this.USER_KEY);
    }
    this.currentUser.set(null);
  }

  getToken(): string | null {
    if (typeof window === 'undefined') {
      return null;
    }
    return localStorage.getItem(this.TOKEN_KEY);
  }

  checkAuthStatus(): Observable<AuthResponse | null> {
    const token = this.getToken();
    if (!token) {
      this.currentUser.set(null);
      return of(null);
    }

    if (this.isDemoSession() && token === 'deployops_demo_token_session') {
      const user = this.currentUser();
      if (user) {
        return of({
          token,
          id: user.id,
          name: user.name,
          email: user.email,
          role: user.role,
          provider: user.provider,
          publicSlug: user.publicSlug
        });
      }
    }

    return this.http.get<AuthResponse>(`${this.authBaseUrl}/me`).pipe(
      tap((res) => this.persistSession(res)),
      catchError(() => {
        this.logout();
        return of(null);
      })
    );
  }

  private persistSession(response: AuthResponse): void {
    const user: AuthUser = {
      id: response.id,
      name: response.name,
      email: response.email,
      role: response.role,
      provider: response.provider,
      publicSlug: response.publicSlug
    };

    if (typeof window !== 'undefined') {
      localStorage.setItem(this.TOKEN_KEY, response.token);
      localStorage.setItem(this.USER_KEY, JSON.stringify(user));
    }

    this.currentUser.set(user);
  }

  private loadStoredUser(): AuthUser | null {
    if (typeof window === 'undefined') {
      return null;
    }

    const raw = localStorage.getItem(this.USER_KEY);
    if (!raw) {
      return null;
    }

    try {
      return JSON.parse(raw) as AuthUser;
    } catch {
      return null;
    }
  }
}
